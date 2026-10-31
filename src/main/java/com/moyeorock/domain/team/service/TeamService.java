package com.moyeorock.domain.team.service;

import com.moyeorock.domain.team.dto.request.TeamCreateRequest;
import com.moyeorock.domain.team.dto.request.TeamMemberStatusUpdateRequest;
import com.moyeorock.domain.team.dto.request.TeamMemberUpdateRequest;
import com.moyeorock.domain.team.dto.request.TeamStatusUpdateRequest;
import com.moyeorock.domain.team.dto.request.TeamUpdateRequest;
import com.moyeorock.domain.team.dto.response.TeamDetailResponse;
import com.moyeorock.domain.team.dto.response.TeamMemberResponse;
import com.moyeorock.domain.team.dto.response.TeamMemberStatusResponse;
import com.moyeorock.domain.team.dto.response.TeamMembersResponse;
import com.moyeorock.domain.team.dto.response.TeamStatusResponse;
import com.moyeorock.domain.team.dto.response.TeamSummaryResponse;
import com.moyeorock.domain.team.entity.Team;
import com.moyeorock.domain.team.entity.TeamGenre;
import com.moyeorock.domain.team.entity.TeamMember;
import com.moyeorock.domain.team.enums.MemberStatus;
import com.moyeorock.domain.team.enums.TeamRole;
import com.moyeorock.domain.team.enums.TeamStatus;
import com.moyeorock.domain.team.repository.TeamGenreRepository;
import com.moyeorock.domain.team.repository.TeamMemberRepository;
import com.moyeorock.domain.team.repository.TeamMemberRepository.TeamMemberCount;
import com.moyeorock.domain.team.repository.TeamRepository;
import com.moyeorock.domain.user.dto.response.UserSummaryResponse;
import com.moyeorock.domain.user.service.UserService;
import com.moyeorock.global.common.dto.PageResponse;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Region;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamGenreRepository teamGenreRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserService userService;

    /** 팀 생성. 요청자가 팀장(LEADER)이 되고 요청의 세션으로 팀원에 등록된다. */
    @Transactional
    public TeamDetailResponse create(Long userId, TeamCreateRequest request) {
        Team team = Team.create(request.name(), request.description(), request.region());
        return registerTeam(userId, team, request.genres(), request.instrument());
    }

    /** 팀 정보 수정(전체 교체). 팀장만 가능하고, 해체된 팀은 고칠 수 없다. */
    @Transactional
    public void update(Long userId, Long teamId, TeamUpdateRequest request) {
        Team team = getTeam(teamId);
        requireLeader(teamId, userId);
        requireActiveTeam(team);

        team.update(request.name(), request.description(), request.region());
        // 벌크 삭제로 기존 장르를 먼저 지워야 같은 장르를 다시 넣어도 유니크 위반이 나지 않는다
        teamGenreRepository.deleteByTeamId(teamId);
        saveGenres(teamId, request.genres());
    }

    /** 팀 해체. teams.status만 바꾼다 — 팀원·합주 등은 그대로 두고 조회에서 걸러진다(architecture.md §6). */
    @Transactional
    public TeamStatusResponse disband(Long userId, Long teamId, TeamStatusUpdateRequest request) {
        if (request.status() != TeamStatus.DISBANDED) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        Team team = getTeam(teamId);
        requireLeader(teamId, userId);
        requireActiveTeam(team);

        team.disband();
        return TeamStatusResponse.from(team);
    }

    /** 팀원 역할·세션 변경. 팀장만 가능하고, 보낸 필드만 반영한다. role = LEADER면 팀장 위임이다. */
    @Transactional
    public TeamMemberResponse changeMember(Long userId, Long teamId, Long targetUserId,
            TeamMemberUpdateRequest request) {
        Team team = getTeam(teamId);
        TeamMember requester = requireLeader(teamId, userId);
        requireActiveTeam(team);
        TeamMember target = getActiveMember(teamId, targetUserId);

        if (request.role() != null) {
            applyRole(requester, target, request.role());
        }
        if (request.instrument() != null) {
            target.changeInstrument(request.instrument());
        }

        UserSummaryResponse user = userService.getSummaries(List.of(targetUserId)).get(targetUserId);
        return TeamMemberResponse.of(target, user);
    }

    /**
     * 팀원 탈퇴(LEFT)·강퇴(REMOVED). 탈퇴는 본인만, 강퇴는 팀장만 할 수 있다.
     * 팀장은 위임 전에는 탈퇴할 수 없고 자신을 강퇴할 수도 없다.
     */
    @Transactional
    public TeamMemberStatusResponse changeMemberStatus(Long userId, Long teamId, Long targetUserId,
            TeamMemberStatusUpdateRequest request) {
        MemberStatus status = request.status();
        if (status != MemberStatus.LEFT && status != MemberStatus.REMOVED) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        Team team = getTeam(teamId);
        if (status == MemberStatus.LEFT) {
            if (!userId.equals(targetUserId)) {
                throw new BusinessException(ErrorCode.NO_PERMISSION);
            }
        } else {
            requireLeader(teamId, userId);
        }
        requireActiveTeam(team);

        TeamMember target = getActiveMember(teamId, targetUserId);
        if (target.getRole() == TeamRole.LEADER) {
            throw new BusinessException(
                    status == MemberStatus.LEFT ? ErrorCode.LEADER_MUST_DELEGATE_FIRST : ErrorCode.INVALID_STATE);
        }

        target.updateStatus(status);
        return TeamMemberStatusResponse.from(target);
    }

    /**
     * 팀 목록. 해체된 팀은 항상 빠진다(결정 8). 정렬은 쿼리에 고정되어 있어 클라이언트의 sort는 무시한다.
     * 목록·장르·인원 수 쿼리 결과가 서로 맞아야 해서 readOnly 트랜잭션으로 묶는다.
     */
    @Transactional(readOnly = true)
    public PageResponse<TeamSummaryResponse> search(Region region, Genre genre, Pageable pageable) {
        Page<Team> teams = teamRepository.search(region, genre, TeamStatus.ACTIVE, withoutSort(pageable));
        return PageResponse.from(toSummaries(teams));
    }

    /** 내 팀 목록. 내가 ACTIVE 팀원이고 팀도 ACTIVE인 팀만, 최근 합류 순이다. */
    @Transactional(readOnly = true)
    public PageResponse<TeamSummaryResponse> getMyTeams(Long userId, Pageable pageable) {
        return PageResponse.from(toSummaries(teamRepository.findMyTeams(userId, withoutSort(pageable))));
    }

    /**
     * 팀 상세. 해체된 팀은 없는 팀(TEAM_NOT_FOUND)으로 취급한다. 중간에 user 도메인 Service를 호출하므로
     * 트랜잭션을 붙이지 않는다(conventions.md §3 — 호출된 Service가 자기 트랜잭션을 열어 바깥에서 감싸도
     * 같은 시점이 보장되지 않는다).
     */
    public TeamDetailResponse getDetail(Long userId, Long teamId) {
        return toDetail(getActiveTeam(teamId), userId);
    }

    /** 팀원 목록. ACTIVE 팀원만 합류 순으로 돌려주며, 해체된 팀은 TEAM_NOT_FOUND다. */
    public TeamMembersResponse getMembers(Long teamId) {
        getActiveTeam(teamId);
        List<TeamMember> members = teamMemberRepository.findActiveMembers(teamId);
        return new TeamMembersResponse(toMemberResponses(members, getUsers(members)));
    }

    // ---------- 타 도메인용 ----------
    // 다른 도메인은 이 메서드들(Service)과 응답 DTO만 쓴다. 요청 DTO는 넘기지 않고 값을 하나씩 받는다(conventions.md §6).

    /**
     * 팀 요약 일괄 조회(3팀 북마크·1팀 내 활동). 해체된 팀과 없는 id는 결과 Map에서 빠지니,
     * 호출하는 쪽이 없는 id를 "삭제된 항목"으로 처리한다.
     */
    @Transactional(readOnly = true)
    public Map<Long, TeamSummaryResponse> getSummaries(List<Long> teamIds) {
        if (teamIds == null || teamIds.isEmpty()) {
            return Map.of();
        }
        return buildSummaries(teamRepository.findActiveByIdIn(teamIds)).stream()
                .collect(Collectors.toMap(TeamSummaryResponse::id, Function.identity()));
    }

    /**
     * 팀원 추가(3팀 가입·초대 승인). 호출한 쪽의 트랜잭션에 참여한다.
     * 탈퇴·강퇴했던 사람은 새 행을 만들지 않고 기존 행을 되살린다(결정 5). 이미 팀원이거나 해체된 팀이면 INVALID_STATE다.
     */
    @Transactional
    public void addMember(Long teamId, Long userId, Instrument instrument) {
        requireActiveTeam(getTeam(teamId));
        teamMemberRepository.findByTeamIdAndUserId(teamId, userId).ifPresentOrElse(
                member -> {
                    if (member.getStatus() == MemberStatus.ACTIVE) {
                        throw new BusinessException(ErrorCode.INVALID_STATE);
                    }
                    member.rejoin(instrument);
                },
                () -> teamMemberRepository.save(TeamMember.create(teamId, userId, TeamRole.MEMBER, instrument)));
    }

    /** 공연 내 팀 생성(4팀). 만든 사람이 팀장이 된다. 공연 존재·상태 검증은 호출하는 4팀의 몫이다. */
    @Transactional
    public TeamDetailResponse createForPerformance(Long userId, Long performanceId, String name,
            String description, Region region, List<Genre> genres, Instrument instrument) {
        Team team = Team.createForPerformance(performanceId, name, description, region);
        return registerTeam(userId, team, genres, instrument);
    }

    /**
     * 공연 상세에 보여줄 팀 요약(4팀). 해체된 팀은 빠지므로 끝난 공연(팀이 모두 해체됨)은 빈 목록이다.
     * 장르·인원 수 쿼리 결과가 서로 맞아야 해서 readOnly 트랜잭션으로 묶는다.
     */
    @Transactional(readOnly = true)
    public List<TeamSummaryResponse> getActiveSummaries(Long performanceId) {
        return buildSummaries(teamRepository.findActiveByPerformanceId(performanceId));
    }

    /** 내가 그 공연에서 속한 팀 id(4팀). 한 사람이 같은 공연의 여러 팀에 속할 수 있어 목록이고, 없으면 빈 목록이다. */
    public List<Long> findMyTeamIds(Long performanceId, Long userId) {
        return teamRepository.findMyTeamIdsInPerformance(performanceId, userId);
    }

    /** 공연 종료 시 소속 팀 전부 해체(4팀). teams.status만 바꾸고 이미 해체된 팀은 건드리지 않는다. */
    @Transactional
    public void disbandAllByPerformance(Long performanceId) {
        teamRepository.findActiveByPerformanceId(performanceId).forEach(Team::disband);
    }

    // 요청 role이 현재와 같으면 아무 것도 안 한다. LEADER로 바꾸면 위임이라 기존 팀장은 MEMBER가 된다.
    private void applyRole(TeamMember leader, TeamMember target, TeamRole role) {
        if (role == target.getRole()) {
            return;
        }
        if (role == TeamRole.LEADER) {
            leader.changeRole(TeamRole.MEMBER);
            target.changeRole(TeamRole.LEADER);
            return;
        }
        // 팀장을 MEMBER로 내리면 팀장이 없는 팀이 된다 — 위임으로만 바꿀 수 있다
        throw new BusinessException(ErrorCode.INVALID_STATE);
    }

    private Team getTeam(Long teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_NOT_FOUND));
    }

    // 조회 전용: 해체된 팀은 조회에서 걸러진다(architecture.md §6)
    private Team getActiveTeam(Long teamId) {
        Team team = getTeam(teamId);
        if (team.getStatus() != TeamStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.TEAM_NOT_FOUND);
        }
        return team;
    }

    private void requireActiveTeam(Team team) {
        if (team.getStatus() != TeamStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_STATE);
        }
    }

    private TeamMember requireLeader(Long teamId, Long userId) {
        return teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
                .filter(m -> m.getStatus() == MemberStatus.ACTIVE && m.getRole() == TeamRole.LEADER)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_TEAM_LEADER));
    }

    // 팀원이 아니거나 이미 탈퇴·강퇴된 대상은 INVALID_STATE로 처리한다.
    private TeamMember getActiveMember(Long teamId, Long userId) {
        return teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
                .filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_STATE));
    }

    // 새 팀과 장르, 팀장 팀원을 저장하고 상세를 만든다. create와 createForPerformance가 함께 쓴다.
    private TeamDetailResponse registerTeam(Long userId, Team newTeam, List<Genre> genres, Instrument instrument) {
        Team team = teamRepository.save(newTeam);
        saveGenres(team.getId(), genres);
        teamMemberRepository.save(TeamMember.create(team.getId(), userId, TeamRole.LEADER, instrument));
        return toDetail(team, userId);
    }

    // 중복 장르는 한 번만 저장한다. (team_id, genre) 유니크 위반을 막는다.
    private void saveGenres(Long teamId, List<Genre> genres) {
        if (genres == null || genres.isEmpty()) {
            return;
        }
        teamGenreRepository.saveAll(genres.stream().distinct().map(genre -> TeamGenre.create(teamId, genre)).toList());
    }

    private TeamDetailResponse toDetail(Team team, Long viewerId) {
        List<Genre> genres = groupGenres(List.of(team.getId())).getOrDefault(team.getId(), List.of());
        List<TeamMember> members = teamMemberRepository.findActiveMembers(team.getId());
        Map<Long, UserSummaryResponse> users = getUsers(members);

        UserSummaryResponse leader = members.stream()
                .filter(m -> m.getRole() == TeamRole.LEADER)
                .findFirst()
                .map(m -> users.get(m.getUserId()))
                .orElse(null);
        TeamRole myRole = members.stream()
                .filter(m -> m.getUserId().equals(viewerId))
                .findFirst()
                .map(TeamMember::getRole)
                .orElse(null);

        return TeamDetailResponse.of(team, genres, leader, toMemberResponses(members, users), myRole);
    }

    private Page<TeamSummaryResponse> toSummaries(Page<Team> teams) {
        return new PageImpl<>(buildSummaries(teams.getContent()), teams.getPageable(), teams.getTotalElements());
    }

    // 항목마다 장르·인원 수를 따로 조회하면 N+1이 되므로 팀 id 목록으로 한 번에 가져온다
    private List<TeamSummaryResponse> buildSummaries(List<Team> teams) {
        if (teams.isEmpty()) {
            return List.of();
        }
        List<Long> teamIds = teams.stream().map(Team::getId).toList();
        Map<Long, List<Genre>> genres = groupGenres(teamIds);
        Map<Long, Integer> counts = countMembers(teamIds);
        return teams.stream()
                .map(team -> TeamSummaryResponse.of(team,
                        genres.getOrDefault(team.getId(), List.of()), counts.getOrDefault(team.getId(), 0)))
                .toList();
    }

    // 장르 행의 id 순(저장한 순서)을 유지한다
    private Map<Long, List<Genre>> groupGenres(List<Long> teamIds) {
        return teamGenreRepository.findAllByTeamIdIn(teamIds).stream()
                .sorted(Comparator.comparing(TeamGenre::getId))
                .collect(Collectors.groupingBy(TeamGenre::getTeamId,
                        Collectors.mapping(TeamGenre::getGenre, Collectors.toList())));
    }

    // 팀원이 0명인 팀은 집계 결과에 행이 없어서 호출하는 쪽이 getOrDefault로 0을 채운다
    private Map<Long, Integer> countMembers(List<Long> teamIds) {
        return teamMemberRepository.countActiveByTeamIds(teamIds).stream()
                .collect(Collectors.toMap(TeamMemberCount::getTeamId, count -> (int) count.getCount()));
    }

    // 탈퇴한 회원은 getSummaries 결과에 없어 user가 null로 내려간다
    private Map<Long, UserSummaryResponse> getUsers(List<TeamMember> members) {
        return userService.getSummaries(members.stream().map(TeamMember::getUserId).toList());
    }

    private List<TeamMemberResponse> toMemberResponses(List<TeamMember> members,
            Map<Long, UserSummaryResponse> users) {
        return members.stream().map(m -> TeamMemberResponse.of(m, users.get(m.getUserId()))).toList();
    }

    // 정렬은 쿼리에 고정되어 있어서 클라이언트가 보낸 sort를 버린다(ORDER BY 중복 방지)
    private Pageable withoutSort(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    }
}

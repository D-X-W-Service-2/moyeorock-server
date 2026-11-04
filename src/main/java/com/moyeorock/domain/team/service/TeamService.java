package com.moyeorock.domain.team.service;

import com.moyeorock.domain.team.dto.request.TeamCreateRequest;
import com.moyeorock.domain.team.dto.request.TeamMemberStatusUpdateRequest;
import com.moyeorock.domain.team.dto.request.TeamMemberUpdateRequest;
import com.moyeorock.domain.team.dto.request.TeamStatusUpdateRequest;
import com.moyeorock.domain.team.dto.request.TeamUpdateRequest;
import com.moyeorock.domain.team.dto.response.PerformanceTeamResponse;
import com.moyeorock.domain.team.dto.response.TeamDetailResponse;
import com.moyeorock.domain.team.dto.response.TeamMemberResponse;
import com.moyeorock.domain.team.dto.response.TeamMembershipResponse;
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
import com.moyeorock.domain.performance.dto.response.PerformanceBriefResponse;
import com.moyeorock.domain.performance.service.PerformanceMemberService;
import com.moyeorock.domain.setlist.service.SetlistService;
import com.moyeorock.domain.user.dto.response.UserSummaryResponse;
import com.moyeorock.domain.user.service.UserService;
import com.moyeorock.global.common.dto.PageResponse;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Region;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import java.util.Collection;
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
    private final PerformanceMemberService performanceMemberService;
    private final SetlistService setlistService;

    /** 팀 생성. 요청자가 팀장(LEADER)이 되고 요청의 세션으로 팀원에 등록된다. */
    @Transactional
    public TeamDetailResponse create(Long userId, TeamCreateRequest request) {
        Team team = Team.create(request.name(), request.description(), request.region());
        return registerTeam(userId, team, request.genres(), request.instrument());
    }

    /** 팀 정보 수정(전체 교체). 팀장만 가능하고, 해체된 팀은 고칠 수 없다. */
    @Transactional
    public void update(Long userId, Long teamId, TeamUpdateRequest request) {
        Team team = getTeamForUpdate(teamId);
        requireLeader(teamId, userId);
        requireActiveTeam(team);

        team.update(request.name(), request.description(), request.region());
        // 벌크 삭제로 기존 장르를 먼저 지워야 같은 장르를 다시 넣어도 유니크 위반이 나지 않는다
        teamGenreRepository.deleteByTeamId(teamId);
        saveGenres(teamId, request.genres());
    }

    /**
     * 팀 해체. teams.status만 바꾼다 — 팀원·합주 등은 그대로 두고 조회에서 걸러진다(architecture.md §6).
     * 다만 그 팀의 선택곡·셋리스트 행은 같은 트랜잭션에서 지운다(4팀 SetlistService.deleteByTeam). 해체된 팀이 곡을 쥐고 있으면
     * 같은 공연의 다른 팀이 그 곡을 넘기거나 고를 수 없기 때문이다. 공연 종료의 일괄 해체(disbandAllByPerformance)는 지우지 않는다.
     */
    @Transactional
    public TeamStatusResponse disband(Long userId, Long teamId, TeamStatusUpdateRequest request) {
        if (request.status() != TeamStatus.DISBANDED) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        Team team = getTeamForUpdate(teamId);
        requireLeader(teamId, userId);
        requireActiveTeam(team);

        team.disband();
        setlistService.deleteByTeam(teamId);
        return TeamStatusResponse.from(team);
    }

    /** 팀원 역할·세션 변경. 팀장만 가능하고, 보낸 필드만 반영한다. role = LEADER면 팀장 위임이다. */
    @Transactional
    public TeamMemberResponse changeMember(Long userId, Long teamId, Long targetUserId,
            TeamMemberUpdateRequest request) {
        Team team = getTeamForUpdate(teamId);
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
        Team team = getTeamForUpdate(teamId);
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
     * 팀 상세. 해체된 팀도 읽기 전용으로 돌려주고 status로 구분한다(끝난 공연 페이지의 팀 카드가 연결된다).
     * 중간에 user 도메인 Service를 호출하므로 트랜잭션을 붙이지 않는다(conventions.md §3 — 호출된 Service가
     * 자기 트랜잭션을 열어 바깥에서 감싸도 같은 시점이 보장되지 않는다).
     */
    public TeamDetailResponse getDetail(Long userId, Long teamId) {
        return toDetail(getTeam(teamId), userId);
    }

    /** 팀원 목록. ACTIVE 팀원만 합류 순으로 돌려주며, 해체된 팀도 읽을 수 있다. */
    public TeamMembersResponse getMembers(Long teamId) {
        getTeam(teamId);
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
    public Map<Long, TeamSummaryResponse> getSummaries(Collection<Long> teamIds) {
        if (teamIds == null || teamIds.isEmpty()) {
            return Map.of();
        }
        return toSummaryMap(teamRepository.findActiveByIdIn(teamIds));
    }

    /**
     * 팀 요약 일괄 조회, 해체된 팀 포함(4팀 끝난 공연 상세). 없는 id만 결과 Map에서 빠진다.
     * 팀원 행은 해체해도 그대로라서 memberCount는 해체 당시 인원이다.
     */
    @Transactional(readOnly = true)
    public Map<Long, TeamSummaryResponse> getSummariesIncludingDisbanded(Collection<Long> teamIds) {
        if (teamIds == null || teamIds.isEmpty()) {
            return Map.of();
        }
        return toSummaryMap(teamRepository.findAllById(teamIds));
    }

    /**
     * 내가 속한 활동 중인 팀마다 팀 요약과 그 팀에서의 내 역할·악기(1팀 내 활동). 최근 합류 순이고 페이징은 없다.
     * 팀원 행, 팀, 장르, 인원 수 쿼리 결과가 서로 맞아야 해서 readOnly 트랜잭션으로 묶는다.
     */
    @Transactional(readOnly = true)
    public List<TeamMembershipResponse> findMemberships(Long userId) {
        List<TeamMember> memberships = teamMemberRepository.findActiveMembershipsOf(userId);
        if (memberships.isEmpty()) {
            return List.of();
        }
        List<Long> teamIds = memberships.stream().map(TeamMember::getTeamId).toList();
        Map<Long, TeamSummaryResponse> summaries = toSummaryMap(teamRepository.findActiveByIdIn(teamIds));
        // 해체된 팀은 요약에 없으므로 그 팀의 팀원 행도 함께 빠진다
        return memberships.stream()
                .filter(m -> summaries.containsKey(m.getTeamId()))
                .map(m -> TeamMembershipResponse.of(summaries.get(m.getTeamId()), m))
                .toList();
    }

    /**
     * 내가 팀장인 활동 중인 팀 요약(1팀 회원 탈퇴 차단). 비어 있어야 탈퇴할 수 있고, 있으면 어느 팀 때문에
     * 막혔는지 안내하는 데 쓴다. 해체된 팀은 빠지고 팀 id 순이며 페이징은 없다.
     * 팀, 장르, 인원 수 쿼리 결과가 서로 맞아야 해서 readOnly 트랜잭션으로 묶는다.
     */
    @Transactional(readOnly = true)
    public List<TeamSummaryResponse> findLedTeams(Long userId) {
        return buildSummaries(teamRepository.findActiveTeamsLedBy(userId));
    }

    /**
     * 팀원 추가(3팀 가입·초대 승인). 호출한 쪽의 트랜잭션에 참여한다.
     * 탈퇴·강퇴했던 사람은 새 행을 만들지 않고 기존 행을 되살린다(결정 5). 이미 팀원이거나 해체된 팀이면 INVALID_STATE다.
     * 공연 팀이면 그 공연을 연 모임의 활동 모임원만 들어올 수 있다(아니면 NOT_PERFORMANCE_GROUP_MEMBER, #96).
     *
     * <p>이 메서드는 첫 조회로 팀 행을 잠그지만, 호출한 쪽의 트랜잭션에 참여하므로 호출한 쪽이 이 메서드를 부르기 전에
     * 다른 조회를 이미 했다면 그 조회 시점의 낡은 화면으로 팀원 정보를 읽을 수 있다(MySQL 기본 격리 수준 때문).
     * 동시 가입·탈퇴가 겹칠 수 있는 승인 흐름에서는 호출하는 쪽도 이 메서드를 가능한 한 트랜잭션 앞쪽에서 불러 달라.
     */
    @Transactional
    public void addMember(Long teamId, Long userId, Instrument instrument) {
        Team team = getTeamForUpdate(teamId);
        requireActiveTeam(team);
        if (team.getPerformanceId() != null) {
            performanceMemberService.ensureGroupMember(team.getPerformanceId(), userId);
        }
        teamMemberRepository.findByTeamIdAndUserId(teamId, userId).ifPresentOrElse(
                member -> {
                    if (member.getStatus() == MemberStatus.ACTIVE) {
                        throw new BusinessException(ErrorCode.INVALID_STATE);
                    }
                    member.rejoin(instrument);
                },
                () -> teamMemberRepository.save(TeamMember.create(teamId, userId, TeamRole.MEMBER, instrument)));
    }

    /**
     * 팀 행만 잠근다(3팀 가입 신청·초대 생성과 승인·수락). 같은 팀에 대한 신청·초대가 한 줄로 서서 같은 사람의 대기 건이
     * 둘 생기지 않고, 승인 흐름이 낡은 화면으로 팀원 여부를 읽지 않는다. 상태는 보지 않는다 — 해체된 팀의 판정은
     * 부르는 쪽의 기존 흐름({@link #addMember} 등)이 그대로 한다. 없는 팀이면 TEAM_NOT_FOUND다.
     * 호출한 쪽의 트랜잭션에 참여하며, 그 트랜잭션의 첫 일반 조회보다 먼저 불러야 한다({@link #getTeamForUpdate}).
     */
    @Transactional
    public void lockTeam(Long teamId) {
        getTeamForUpdate(teamId);
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

    /**
     * 내가 속한 공연 팀(4팀 끝난 공연 상세의 내 팀). 내가 ACTIVE 팀원이면 팀이 해체됐어도 담기고(status로 구분),
     * 독립 팀은 빠진다. 팀 id 순이고 페이징은 없다.
     */
    @Transactional(readOnly = true)
    public List<PerformanceTeamResponse> findMyPerformanceTeams(Long userId) {
        return teamRepository.findMyPerformanceTeams(userId).stream().map(PerformanceTeamResponse::from).toList();
    }

    /**
     * 모임 탈퇴·강퇴 시 그 모임 공연들의 팀에서 이 사람을 뺀다(4팀 GroupService). 호출한 쪽의 트랜잭션에 참여한다.
     * 활동 중인 팀의 ACTIVE 팀원 행을 LEFT로 바꾸고, 해체된 팀과 이미 나간 팀은 지나간다.
     * 이 사람이 그중 한 팀의 팀장이면 아무것도 바꾸지 않고 LEADER_MUST_DELEGATE_FIRST를 던져 모임 탈퇴·강퇴 전체를 막는다.
     *
     * <p>팀 행을 id 순으로 잠그고 시작한다. 호출한 쪽이 이 메서드 전에 팀 조회를 했다면 그 시점의 낡은 화면을 볼 수 있으니
     * 가능한 한 트랜잭션 앞쪽에서 불러 달라(addMember와 같다).
     */
    @Transactional
    public void leavePerformanceTeams(Long userId, Collection<Long> performanceIds) {
        if (performanceIds == null || performanceIds.isEmpty()) {
            return;
        }
        leaveLockedTeams(userId, teamRepository.findActiveByPerformanceIdInForUpdate(performanceIds));
    }

    /**
     * 회원 탈퇴 시 이 사람이 속한 모든 활동 중인 팀(공연 팀·독립 팀)에서 뺀다(1팀 UserService). 호출한 쪽의 트랜잭션에 참여한다.
     * ACTIVE 팀원 행을 LEFT로 바꾸고, 해체된 팀은 지나간다 — 해체된 팀의 팀원 행은 해체 당시 인원 기록이라 그대로 둔다.
     * 팀장 행이 남아 있으면 아무것도 바꾸지 않고 LEADER_MUST_DELEGATE_FIRST를 던진다. 1팀이 먼저 findLedTeams로 막으므로
     * 보통은 일반 팀원만 남지만, 그 확인 뒤에 위임·합류가 겹친 경우를 막는 안전장치다.
     *
     * <p>팀원 행을 읽어 팀을 알아낸 다음 팀 행을 id 순으로 잠그고, 팀원 행은 잠금 읽기로 다시 읽는다. 그래서 그 사이에
     * 바뀐 역할도 놓치지 않는다. 확인 이후에 새로 합류한 팀은 이번에 빠지지 않는다.
     */
    @Transactional
    public void leaveAllTeams(Long userId) {
        List<Long> teamIds = teamMemberRepository.findActiveMembershipsOf(userId).stream()
                .map(TeamMember::getTeamId)
                .toList();
        if (teamIds.isEmpty()) {
            return;
        }
        leaveLockedTeams(userId, teamRepository.findActiveByIdInForUpdate(teamIds));
    }

    // 이미 잠근 활동 중인 팀들에서 이 사람의 ACTIVE 팀원 행을 LEFT로 바꾼다. 팀장 행이 하나라도 있으면 아무것도 바꾸지 않는다.
    private void leaveLockedTeams(Long userId, List<Team> lockedTeams) {
        if (lockedTeams.isEmpty()) {
            return;
        }
        List<Long> teamIds = lockedTeams.stream().map(Team::getId).toList();
        List<TeamMember> memberships = teamMemberRepository.findActiveByUserIdAndTeamIdInForUpdate(userId, teamIds);
        if (memberships.stream().anyMatch(m -> m.getRole() == TeamRole.LEADER)) {
            throw new BusinessException(ErrorCode.LEADER_MUST_DELEGATE_FIRST);
        }
        memberships.forEach(m -> m.updateStatus(MemberStatus.LEFT));
    }

    /** 공연 종료 시 소속 팀 전부 해체(4팀). teams.status만 바꾸고 이미 해체된 팀은 건드리지 않는다. */
    @Transactional
    public void disbandAllByPerformance(Long performanceId) {
        teamRepository.findActiveByPerformanceIdForUpdate(performanceId).forEach(Team::disband);
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

    // 팀의 상태나 팀원 구성을 바꾸는 요청은 팀 행을 먼저 잠근다. 같은 팀에 대한 요청이 한 줄로 서므로
    // 동시 위임으로 팀장이 둘이 되거나, 수정이 해체를 옛 값으로 덮어쓰는 일이 생기지 않는다.
    // 반드시 메서드의 첫 조회로 불러야 한다(앞서 다른 조회가 있으면 그 시점의 낡은 화면을 볼 수 있다).
    private Team getTeamForUpdate(Long teamId) {
        return teamRepository.findByIdForUpdate(teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_NOT_FOUND));
    }

    private Team getTeam(Long teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_NOT_FOUND));
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

    // 팀원이 아니거나 이미 탈퇴·강퇴된 대상은 TEAM_MEMBER_NOT_FOUND(404)로 처리한다.
    private TeamMember getActiveMember(Long teamId, Long userId) {
        return teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
                .filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_MEMBER_NOT_FOUND));
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

        // 독립 팀은 소속 공연이 없다. 해체된 모임의 공연이면 모임 이름만 null로 내려간다.
        PerformanceBriefResponse performance = team.getPerformanceId() == null
                ? null
                : performanceMemberService.getBrief(team.getPerformanceId());

        return TeamDetailResponse.of(team, genres, performance, leader, toMemberResponses(members, users),
                setlistService.findTeamSongs(team.getId()), myRole);
    }

    private Map<Long, TeamSummaryResponse> toSummaryMap(List<Team> teams) {
        return buildSummaries(teams).stream().collect(Collectors.toMap(TeamSummaryResponse::id, Function.identity()));
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

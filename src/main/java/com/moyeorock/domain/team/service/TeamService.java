package com.moyeorock.domain.team.service;

import com.moyeorock.domain.team.dto.request.TeamCreateRequest;
import com.moyeorock.domain.team.dto.request.TeamMemberStatusUpdateRequest;
import com.moyeorock.domain.team.dto.request.TeamMemberUpdateRequest;
import com.moyeorock.domain.team.dto.request.TeamStatusUpdateRequest;
import com.moyeorock.domain.team.dto.request.TeamUpdateRequest;
import com.moyeorock.domain.team.dto.response.TeamDetailResponse;
import com.moyeorock.domain.team.dto.response.TeamMemberResponse;
import com.moyeorock.domain.team.dto.response.TeamMemberStatusResponse;
import com.moyeorock.domain.team.dto.response.TeamStatusResponse;
import com.moyeorock.domain.team.entity.Team;
import com.moyeorock.domain.team.entity.TeamGenre;
import com.moyeorock.domain.team.entity.TeamMember;
import com.moyeorock.domain.team.enums.MemberStatus;
import com.moyeorock.domain.team.enums.TeamRole;
import com.moyeorock.domain.team.enums.TeamStatus;
import com.moyeorock.domain.team.repository.TeamGenreRepository;
import com.moyeorock.domain.team.repository.TeamMemberRepository;
import com.moyeorock.domain.team.repository.TeamRepository;
import com.moyeorock.domain.user.dto.response.UserSummaryResponse;
import com.moyeorock.domain.user.service.UserService;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
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
        Team team = teamRepository.save(Team.create(request.name(), request.description(), request.region()));
        saveGenres(team.getId(), request.genres());
        teamMemberRepository.save(TeamMember.create(team.getId(), userId, TeamRole.LEADER, request.instrument()));
        return toDetail(team, userId);
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

    // 중복 장르는 한 번만 저장한다. (team_id, genre) 유니크 위반을 막는다.
    private void saveGenres(Long teamId, List<Genre> genres) {
        if (genres == null || genres.isEmpty()) {
            return;
        }
        teamGenreRepository.saveAll(genres.stream().distinct().map(genre -> TeamGenre.create(teamId, genre)).toList());
    }

    private TeamDetailResponse toDetail(Team team, Long viewerId) {
        List<Genre> genres = teamGenreRepository.findAllByTeamIdIn(List.of(team.getId())).stream()
                .sorted(Comparator.comparing(TeamGenre::getId))
                .map(TeamGenre::getGenre)
                .toList();
        List<TeamMember> members = teamMemberRepository.findActiveMembers(team.getId());
        // 탈퇴한 회원은 getSummaries 결과에 없어 user가 null로 내려간다
        Map<Long, UserSummaryResponse> users =
                userService.getSummaries(members.stream().map(TeamMember::getUserId).toList());

        List<TeamMemberResponse> memberResponses = members.stream()
                .map(m -> TeamMemberResponse.of(m, users.get(m.getUserId())))
                .toList();
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

        return TeamDetailResponse.of(team, genres, leader, memberResponses, myRole);
    }
}

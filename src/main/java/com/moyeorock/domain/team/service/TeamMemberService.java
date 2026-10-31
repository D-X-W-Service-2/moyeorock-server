package com.moyeorock.domain.team.service;

import com.moyeorock.domain.team.entity.Team;
import com.moyeorock.domain.team.enums.MemberStatus;
import com.moyeorock.domain.team.enums.TeamRole;
import com.moyeorock.domain.team.enums.TeamStatus;
import com.moyeorock.domain.team.repository.TeamMemberRepository;
import com.moyeorock.domain.team.repository.TeamRepository;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 다른 도메인(셋리스트 등)이 팀 정보를 확인하려고 부르는 작은 조회 서비스다(4팀 요청).
 *
 * <p>다른 Service를 부르지 않는 것이 이 클래스의 조건이다. TeamService가 셋리스트 Service를 부르고
 * 셋리스트 Service도 팀 정보가 필요해서, 확인 용도만 여기로 떼어 두어야 서비스끼리 서로 부르는 순환이 생기지 않는다
 * (architecture.md §3).
 */
@Service
@RequiredArgsConstructor
public class TeamMemberService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;

    /**
     * 팀장인지 확인한다. 없는 팀이나 해체된 팀은 TEAM_NOT_FOUND, 활동 중인 팀장이 아니면 NOT_TEAM_LEADER다.
     * 호출한 쪽의 트랜잭션에 참여한다.
     */
    public void ensureLeader(Long teamId, Long userId) {
        getActiveTeam(teamId);
        teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
                .filter(m -> m.getStatus() == MemberStatus.ACTIVE && m.getRole() == TeamRole.LEADER)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_TEAM_LEADER));
    }

    /**
     * 활동 중인 팀원인지 확인한다(역할은 묻지 않는다). 없는 팀이나 해체된 팀은 TEAM_NOT_FOUND,
     * 팀원이 아니거나 이미 나간 사람은 NO_PERMISSION이다(새 에러 코드는 만들지 않았다).
     */
    public void ensureActiveMember(Long teamId, Long userId) {
        getActiveTeam(teamId);
        teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
                .filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_PERMISSION));
    }

    /**
     * 팀이 속한 공연 id. 독립 팀이면 null이다. 없는 팀만 TEAM_NOT_FOUND이고 해체된 팀도 돌려준다 —
     * 해체 여부 검사는 위의 ensure 메서드를 먼저 부르는 쪽이 한다.
     */
    public Long getPerformanceId(Long teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_NOT_FOUND))
                .getPerformanceId();
    }

    /**
     * 팀 이름 일괄 조회. <b>해체된 팀도 포함한다.</b> 끝난 공연의 셋리스트에는 팀이 모두 해체된 뒤에도
     * 팀 이름을 보여줘야 해서다. 없는 id는 결과에서 빠진다. 이름 외의 정보는 돌려주지 않으므로 팀원 정보가 새지 않는다.
     */
    public Map<Long, String> getNames(Collection<Long> teamIds) {
        if (teamIds == null || teamIds.isEmpty()) {
            return Map.of();
        }
        return teamRepository.findAllById(teamIds).stream()
                .collect(Collectors.toMap(Team::getId, Team::getName));
    }

    private Team getActiveTeam(Long teamId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_NOT_FOUND));
        if (team.getStatus() != TeamStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.TEAM_NOT_FOUND);
        }
        return team;
    }
}

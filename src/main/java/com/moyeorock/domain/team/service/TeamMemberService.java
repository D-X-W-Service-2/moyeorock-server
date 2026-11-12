package com.moyeorock.domain.team.service;

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
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 다른 도메인(셋리스트 등)이 팀 정보를 확인하려고 부르는 작은 조회 서비스다(4팀 요청).
 *
 * <p>다른 Service를 부르지 않는 것이 이 클래스의 조건이다. TeamService가 셋리스트 Service를 부르고
 * 셋리스트 Service도 팀 정보가 필요해서, 확인 용도만 여기로 떼어 두어야 서비스끼리 서로 부르는 순환이 생기지 않는다
 * (architecture.md §3).
 *
 * <p>팀 요약(이름·지역·장르·인원 수) 조립도 여기에 있다. 공고가 팀 해체 때 불려야 해서(#178) 공고 Service가
 * TeamService를 주입받으면 순환이 되므로, 공고는 요약과 공연 팀 여부를 이 서비스로 읽는다. TeamService도 같은 조립을 쓴다.
 */
@Service
@RequiredArgsConstructor
public class TeamMemberService {

    private final TeamRepository teamRepository;
    private final TeamGenreRepository teamGenreRepository;
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

    /** 팀장의 user id. 없는 팀이나 해체된 팀은 TEAM_NOT_FOUND다. 알림 수신자를 정할 때 쓴다(3팀). */
    public Long getLeaderId(Long teamId) {
        getActiveTeam(teamId);
        return teamMemberRepository.findActiveMembers(teamId).stream()
                .filter(m -> m.getRole() == TeamRole.LEADER)
                .findFirst()
                .map(TeamMember::getUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_NOT_FOUND));
    }

    /**
     * 요청자가 이 팀의 활동 중인 팀원인지(역할은 묻지 않는다). 없는 팀·해체된 팀·팀원이 아닌 경우는 {@code false}다.
     * 막지 않고 답만 한다 — 가입 신청·초대의 "이미 소속" 판단에 쓴다. 막아야 하면 {@link #ensureActiveMember}다.
     */
    public boolean isActiveMember(Long teamId, Long userId) {
        return teamRepository.findById(teamId)
                .filter(team -> team.getStatus() == TeamStatus.ACTIVE)
                .flatMap(team -> teamMemberRepository.findByTeamIdAndUserId(teamId, userId))
                .filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                .isPresent();
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
     * 활동 중인 팀장인지 여부만 돌려준다. ensureLeader와 달리 예외를 던지지 않아서, 화면에 보여줄 권한 표시
     * (예: 합주 상세의 canEdit)를 계산할 때 쓴다. 팀의 존재·해체 여부는 보지 않으니 필요하면 ensure 메서드를 먼저 부른다.
     */
    public boolean isLeader(Long teamId, Long userId) {
        return teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
                .filter(m -> m.getStatus() == MemberStatus.ACTIVE && m.getRole() == TeamRole.LEADER)
                .isPresent();
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

    /**
     * 공연 팀(모임 공연에 속한 팀)인지(3팀 공고 작성 차단, #174). 없는 팀이나 독립 팀이면 false다.
     * 팀 존재·해체 여부는 보지 않으니 호출하는 쪽이 따로 확인한다.
     */
    public boolean isPerformanceTeam(Long teamId) {
        return teamRepository.existsByIdAndPerformanceIdIsNotNull(teamId);
    }

    /**
     * 팀 요약 일괄 조회(3팀 공고 등). 해체된 팀과 없는 id는 결과 Map에서 빠지니, 호출하는 쪽이 없는 id를
     * "삭제된 항목"으로 처리한다. 쿼리는 팀·장르·인원 수 각 1번이다.
     */
    public Map<Long, TeamSummaryResponse> getSummaries(Collection<Long> teamIds) {
        if (teamIds == null || teamIds.isEmpty()) {
            return Map.of();
        }
        return summarize(teamRepository.findActiveByIdIn(teamIds)).stream()
                .collect(Collectors.toMap(TeamSummaryResponse::id, Function.identity()));
    }

    /** 항목마다 장르·인원 수를 따로 조회하면 N+1이 되므로 팀 id 목록으로 한 번에 가져와 요약으로 조립한다. */
    public List<TeamSummaryResponse> summarize(List<Team> teams) {
        if (teams.isEmpty()) {
            return List.of();
        }
        List<Long> teamIds = teams.stream().map(Team::getId).toList();
        Map<Long, List<Genre>> genres = getGenres(teamIds);
        Map<Long, Integer> counts = countMembers(teamIds);
        return teams.stream()
                .map(team -> TeamSummaryResponse.of(team,
                        genres.getOrDefault(team.getId(), List.of()), counts.getOrDefault(team.getId(), 0)))
                .toList();
    }

    /** 팀별 장르. 장르 행의 id 순(저장한 순서)을 유지한다. 장르가 없는 팀은 결과에 키가 없다. */
    public Map<Long, List<Genre>> getGenres(List<Long> teamIds) {
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

    private Team getActiveTeam(Long teamId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_NOT_FOUND));
        if (team.getStatus() != TeamStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.TEAM_NOT_FOUND);
        }
        return team;
    }
}

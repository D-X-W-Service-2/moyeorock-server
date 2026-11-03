package com.moyeorock.domain.team.dto.response;

import com.moyeorock.domain.team.entity.Team;
import com.moyeorock.domain.team.enums.TeamStatus;

/**
 * 내가 속한 공연 팀 하나. 4팀의 끝난 공연 상세(myTeamIds)가 쓴다.
 * 해체된 팀도 담기므로 status로 구분한다.
 */
public record PerformanceTeamResponse(Long teamId, String teamName, Long performanceId, TeamStatus status) {

    // 팀 엔티티 하나로 만든다 → from
    public static PerformanceTeamResponse from(Team team) {
        return new PerformanceTeamResponse(team.getId(), team.getName(), team.getPerformanceId(), team.getStatus());
    }
}

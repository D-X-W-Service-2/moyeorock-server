package com.moyeorock.domain.team.dto.response;

import com.moyeorock.domain.team.entity.Team;
import com.moyeorock.domain.team.enums.TeamStatus;

public record TeamStatusResponse(Long id, TeamStatus status) {

    public static TeamStatusResponse from(Team team) {
        return new TeamStatusResponse(team.getId(), team.getStatus());
    }
}

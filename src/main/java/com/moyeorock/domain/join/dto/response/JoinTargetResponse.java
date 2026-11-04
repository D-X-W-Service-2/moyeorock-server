package com.moyeorock.domain.join.dto.response;

import com.moyeorock.domain.group.dto.response.GroupBriefResponse;
import com.moyeorock.domain.team.dto.response.TeamSummaryResponse;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Region;
import java.util.List;

/**
 * 가입 대상(팀·모임) 요약. 모임은 {@code GroupBriefResponse}가 이름만 줘서 region·genres가 null이다
 * (4팀 일괄 조회가 생기면 채운다 — 공고의 target과 같다). 해체됐거나 없는 대상은 id만 남는다.
 */
public record JoinTargetResponse(Long id, String name, Region region, List<Genre> genres) {

    public static JoinTargetResponse ofTeam(TeamSummaryResponse team) {
        return new JoinTargetResponse(team.id(), team.name(), team.region(), team.genres());
    }

    public static JoinTargetResponse ofGroup(GroupBriefResponse group) {
        return new JoinTargetResponse(group.id(), group.name(), null, null);
    }

    public static JoinTargetResponse unresolved(Long id) {
        return new JoinTargetResponse(id, null, null, null);
    }
}

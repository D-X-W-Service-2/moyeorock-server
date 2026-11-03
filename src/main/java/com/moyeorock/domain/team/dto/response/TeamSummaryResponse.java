package com.moyeorock.domain.team.dto.response;

import com.moyeorock.domain.team.entity.Team;
import com.moyeorock.domain.team.enums.TeamStatus;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Region;
import java.util.List;

/**
 * 팀 요약. 목록 항목이며 bookmark·activity·performance 등 다른 도메인도 재사용한다.
 * 필드는 dto-spec 공용 참조 표의 제안 그대로다(4팀 요청). getSummaries 등은 해체된 팀을 빼서 status가 ACTIVE지만, getSummariesIncludingDisbanded는 DISBANDED도 담는다.
 */
public record TeamSummaryResponse(
        Long id,
        String name,
        Region region,
        List<Genre> genres,
        TeamStatus status,
        int memberCount
) {

    public static TeamSummaryResponse of(Team team, List<Genre> genres, int memberCount) {
        return new TeamSummaryResponse(team.getId(), team.getName(), team.getRegion(), genres, team.getStatus(),
                memberCount);
    }
}

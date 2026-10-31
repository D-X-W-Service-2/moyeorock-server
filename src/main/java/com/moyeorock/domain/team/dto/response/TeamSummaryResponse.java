package com.moyeorock.domain.team.dto.response;

import com.moyeorock.domain.team.entity.Team;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Region;
import java.util.List;

/**
 * 팀 요약. 목록 항목이며 bookmark·activity 등 다른 도메인도 재사용한다.
 * 해체된 팀은 요약 조회에서 빠지므로 status는 두지 않는다(노션 팀 목록 응답 기준).
 */
public record TeamSummaryResponse(Long id, String name, Region region, List<Genre> genres, int memberCount) {

    public static TeamSummaryResponse of(Team team, List<Genre> genres, int memberCount) {
        return new TeamSummaryResponse(team.getId(), team.getName(), team.getRegion(), genres, memberCount);
    }
}

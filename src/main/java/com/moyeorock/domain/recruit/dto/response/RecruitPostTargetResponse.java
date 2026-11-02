package com.moyeorock.domain.recruit.dto.response;

import com.moyeorock.domain.group.dto.response.GroupBriefResponse;
import com.moyeorock.domain.recruit.entity.RecruitPost;
import com.moyeorock.domain.team.dto.response.TeamSummaryResponse;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Region;
import java.util.List;

/**
 * dto-naming.md §5: target은 TeamSummaryResponse가 아닌 recruit 자체 축약형이고, Summary·Detail 양쪽에서
 * 재사용하므로 최상위로 둔다(§0 규칙 6).
 *
 * <p>Notion 명세는 target을 non-null 객체로 정의한다. 팀·모임이 해체되거나 없어서 요약을 못 구한 경우에도
 * 객체는 남기고 id만 채운다({@link #unresolved}) — name이 없으면 프론트가 "삭제된 팀/모임"으로 표시한다.
 * 모임은 {@link GroupBriefResponse}가 이름만 줘서 region·genres가 null이다(4팀 일괄 조회가 생기면 채운다).
 */
public record RecruitPostTargetResponse(Long id, String name, Region region, List<Genre> genres) {

    public static RecruitPostTargetResponse ofTeam(TeamSummaryResponse team) {
        return new RecruitPostTargetResponse(team.id(), team.name(), team.region(), team.genres());
    }

    public static RecruitPostTargetResponse ofGroup(GroupBriefResponse group) {
        return new RecruitPostTargetResponse(group.id(), group.name(), null, null);
    }

    public static RecruitPostTargetResponse unresolved(RecruitPost post) {
        return new RecruitPostTargetResponse(post.getTargetId(), null, null, null);
    }
}

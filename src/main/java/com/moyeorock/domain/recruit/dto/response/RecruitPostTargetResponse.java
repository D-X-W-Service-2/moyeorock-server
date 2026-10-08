package com.moyeorock.domain.recruit.dto.response;

import com.moyeorock.domain.recruit.entity.RecruitPost;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Region;
import java.util.List;

// dto-naming.md §5: target은 TeamSummaryResponse가 아닌 recruit 자체 축약형이고, Summary·Detail 양쪽에서
// 재사용하므로 최상위로 둔다(§0 규칙 6).
//
// name·region·genres는 지금 항상 null이다 — 팀이면 2팀 TeamService, 모임이면 4팀 GroupService의 요약
// 조회가 있어야 채울 수 있는데 아직 머지된 게 없다(Notion 명세는 non-null). 둘이 생기면
// from(post) 대신 of(post, summary)로 바꿔 채운다.
public record RecruitPostTargetResponse(Long id, String name, Region region, List<Genre> genres) {

    public static RecruitPostTargetResponse from(RecruitPost post) {
        return new RecruitPostTargetResponse(post.getTargetId(), null, null, null);
    }
}

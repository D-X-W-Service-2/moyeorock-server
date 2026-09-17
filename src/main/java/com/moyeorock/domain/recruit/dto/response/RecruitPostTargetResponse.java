package com.moyeorock.domain.recruit.dto.response;

import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Region;
import java.util.List;

// dto-naming.md §5: "target을 TeamSummaryResponse가 아닌 자체 축약형으로 담는다" — recruit 소유의
// 별도 타입. RecruitPostSummaryResponse·RecruitPostDetailResponse 양쪽에서 재사용하므로 최상위로 둔다
// (dto-naming.md §0 규칙 6).
//
// name·region·genres는 항상 null이다: 팀이면 2팀 TeamService, 모임이면 4팀 GroupService의 요약
//조회가 있어야 채울 수 있는데 두 Service가 아직 없다. 두 Service가 생기면 RecruitPostService에서
// targetType에 따라 호출해 채운다.
public record RecruitPostTargetResponse(Long id, String name, Region region, List<Genre> genres) {

    public static RecruitPostTargetResponse idOnly(Long targetId) {
        return new RecruitPostTargetResponse(targetId, null, null, null);
    }
}

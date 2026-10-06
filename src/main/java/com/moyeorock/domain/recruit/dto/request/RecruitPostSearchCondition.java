package com.moyeorock.domain.recruit.dto.request;

import com.moyeorock.domain.recruit.enums.RecruitStatus;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Region;
import com.moyeorock.global.common.enums.TargetType;

// 조건이 4개라 컨트롤러 파라미터로 풀지 않고 묶는다(dto-naming.md §12). 전부 선택이고, null이면 그 조건은 안 건다.
public record RecruitPostSearchCondition(
        TargetType targetType,
        Region region,
        Instrument instrument,
        RecruitStatus status
) {
}

package com.moyeorock.domain.recruit.dto.response;

import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.TargetType;
import java.util.List;

/** join 도메인이 공고 기반 신청을 검증할 때 쓰는 최소 정보 — 엔티티를 도메인 밖으로 내보내지 않으려는 용도다. */
public record RecruitPostApplyInfoResponse(
        Long id,
        Long authorId,
        TargetType targetType,
        Long targetId,
        List<Instrument> wantedInstruments
) {
}

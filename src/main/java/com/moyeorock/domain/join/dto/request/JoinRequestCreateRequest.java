package com.moyeorock.domain.join.dto.request;

import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.TargetType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// instrument는 Notion 초안이 List("다중 고려")로 적었지만 엔티티·응답이 단수라 단수로 확정했다.
// TEAM 대상은 필수, GROUP 대상은 쓰지 않는다 — 조건부 필수는 Service가 확인한다.
public record JoinRequestCreateRequest(
        @NotNull TargetType targetType,
        @NotNull Long targetId,
        Long recruitPostId,
        Instrument instrument,
        @Size(max = 500) String message
) {
}

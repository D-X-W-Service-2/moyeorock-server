package com.moyeorock.domain.performance.dto.request;

import com.moyeorock.domain.performance.enums.PerformanceStatus;
import jakarta.validation.constraints.NotNull;

// 값은 ERD enum(PLANNED·RECRUITING·DONE·CANCELED) 그대로다. 노션의 ONGOING·ENDED·CANCELLED는 받지 않는다(api-spec.md 공백 #13).
public record PerformanceStatusUpdateRequest(
        @NotNull(message = "공연 상태는 필수입니다.")
        PerformanceStatus status
) {
}

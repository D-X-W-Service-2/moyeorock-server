package com.moyeorock.domain.performance.dto.response;

import com.moyeorock.domain.performance.entity.Performance;
import com.moyeorock.domain.performance.enums.PerformanceStatus;

public record PerformanceStatusResponse(Long id, PerformanceStatus status) {

    public static PerformanceStatusResponse from(Performance performance) {
        return new PerformanceStatusResponse(performance.getId(), performance.getStatus());
    }
}

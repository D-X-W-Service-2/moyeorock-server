package com.moyeorock.domain.performance.dto.response;

import com.moyeorock.domain.performance.entity.Performance;
import com.moyeorock.domain.performance.enums.PerformanceStatus;
import java.time.LocalDateTime;

/**
 * 모임 공연 목록 항목. 노션 API 초안(2026-10-08) 기준으로 {@code teamCount}는 없고 {@code createdAt}이 있다.
 * dto-naming.md §12 재사용 상위 5개라 필드를 바꿀 때는 공유한다.
 */
public record PerformanceSummaryResponse(
        Long id,
        String title,
        LocalDateTime performedAt,
        String venue,
        String posterImage,
        PerformanceStatus status,
        LocalDateTime createdAt
) {

    public static PerformanceSummaryResponse from(Performance performance) {
        return new PerformanceSummaryResponse(
                performance.getId(),
                performance.getTitle(),
                performance.getPerformedAt(),
                performance.getVenue(),
                performance.getPosterImage(),
                performance.getStatus(),
                performance.getCreatedAt()
        );
    }
}

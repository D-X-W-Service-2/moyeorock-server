package com.moyeorock.domain.performance.dto.response;

import com.moyeorock.domain.performance.entity.Performance;
import java.time.LocalDateTime;

/**
 * 공연 표시용 요약 — 2팀 팀 상세의 {@code performance} 필드가 쓴다(dto-spec §2, #105).
 * {@code groupName}은 모임이 해체됐으면 {@code null}이다(팀 상세가 깨지지 않게).
 */
public record PerformanceBriefResponse(Long id, String title, String groupName, LocalDateTime performedAt) {

    // 모임 이름은 group 도메인 Service에서 받아 오므로 엔티티 하나로는 만들 수 없다 → of
    public static PerformanceBriefResponse of(Performance performance, String groupName) {
        return new PerformanceBriefResponse(performance.getId(), performance.getTitle(), groupName,
                performance.getPerformedAt());
    }
}

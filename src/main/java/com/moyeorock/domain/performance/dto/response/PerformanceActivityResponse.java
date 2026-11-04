package com.moyeorock.domain.performance.dto.response;

import com.moyeorock.domain.performance.entity.Performance;
import com.moyeorock.domain.performance.enums.PerformanceStatus;
import java.time.LocalDateTime;

/**
 * 내 활동의 참여 중인 공연 한 줄(1팀 내 활동 #90에 넘기는 값, PR #84 답글 2026-11-02).
 * 한 공연에 내 팀이 여럿이면 팀마다 한 줄이라 {@code teamName}만 다른 같은 공연이 여러 번 나올 수 있다.
 * {@code groupName}·{@code teamName}은 화면에 그대로 나가는 값이라 id 대신 이름으로 준다.
 */
public record PerformanceActivityResponse(
        Long id,
        String title,
        String groupName,
        LocalDateTime performedAt,
        PerformanceStatus status,
        String teamName
) {

    // 공연 엔티티에 다른 도메인에서 받은 모임·팀 이름을 합쳐 만든다 → of
    public static PerformanceActivityResponse of(Performance performance, String groupName, String teamName) {
        return new PerformanceActivityResponse(
                performance.getId(),
                performance.getTitle(),
                groupName,
                performance.getPerformedAt(),
                performance.getStatus(),
                teamName
        );
    }
}

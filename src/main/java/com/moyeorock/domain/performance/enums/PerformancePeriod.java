package com.moyeorock.domain.performance.enums;

import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 모임 공연 목록의 다가올·지난 구분(PR #84 리뷰, 2026-11-02). performances.status를 목록에서만 묶고 응답 status는 그대로다.
// 기준이 날짜가 아니라 상태라서, 모임장이 DONE으로 바꾸기 전에는 날짜가 지난 공연도 UPCOMING에 남는다.
@Getter
@RequiredArgsConstructor
public enum PerformancePeriod {
    UPCOMING(List.of(PerformanceStatus.PLANNED, PerformanceStatus.RECRUITING)),
    PAST(List.of(PerformanceStatus.DONE, PerformanceStatus.CANCELED));

    private final List<PerformanceStatus> statuses;
}

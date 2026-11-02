package com.moyeorock.domain.performance.repository;

import com.moyeorock.domain.performance.entity.Performance;
import com.moyeorock.domain.performance.enums.PerformanceStatus;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PerformanceRepository extends JpaRepository<Performance, Long> {

    // 모임 공연 목록 — 공연일 늦은 순. 공연일이 같은 공연이 페이지 경계에 걸려도 중복·누락이 없게 끝에 id를 붙인다.
    Page<Performance> findByGroupIdOrderByPerformedAtDescIdDesc(Long groupId, Pageable pageable);

    // 다가올 공연(period=UPCOMING) — 공연일 가까운 순. 공연일이 같으면 먼저 만든 공연이 먼저다.
    Page<Performance> findByGroupIdAndStatusInOrderByPerformedAtAscIdAsc(Long groupId,
            Collection<PerformanceStatus> statuses, Pageable pageable);

    // 지난 공연(period=PAST) — 공연일 최근 순. 공연일이 같으면 나중에 만든 공연이 먼저다.
    Page<Performance> findByGroupIdAndStatusInOrderByPerformedAtDescIdDesc(Long groupId,
            Collection<PerformanceStatus> statuses, Pageable pageable);
}

package com.moyeorock.domain.performance.repository;

import com.moyeorock.domain.performance.entity.Performance;
import com.moyeorock.domain.performance.enums.PerformanceStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PerformanceRepository extends JpaRepository<Performance, Long> {

    /**
     * 셋리스트 넘기기용 조회. 강제 증가 낙관적 락이라 커밋 때 "버전이 읽을 때 그대로면 +1" UPDATE를 한다 —
     * 그사이 모임장의 확정·해제가 먼저 커밋돼 버전이 올라가 있으면 0행이 바뀌어 넘기기가 실패한다(전역 처리에서 409).
     * 일반 OPTIMISTIC은 커밋 때 버전을 SELECT로 다시 읽는데, MySQL 기본 격리 수준(REPEATABLE READ)에서는 그 SELECT가
     * 트랜잭션 시작 시점의 스냅샷을 읽어 바뀐 버전을 못 본다(PerformanceSetlistConcurrencyTest로 확인, 이슈 #103).
     * 대신 같은 공연에 두 팀이 동시에 넘기면 늦은 쪽도 409가 된다 — 다시 넘기면 된다.
     */
    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("SELECT p FROM Performance p WHERE p.id = :id")
    Optional<Performance> findByIdForSetlistSubmit(@Param("id") Long id);

    // 모임 공연 목록 — 공연일 늦은 순. 공연일이 같은 공연이 페이지 경계에 걸려도 중복·누락이 없게 끝에 id를 붙인다.
    Page<Performance> findByGroupIdOrderByPerformedAtDescIdDesc(Long groupId, Pageable pageable);

    // 다가올 공연(period=UPCOMING) — 공연일 가까운 순. 공연일이 같으면 먼저 만든 공연이 먼저다.
    Page<Performance> findByGroupIdAndStatusInOrderByPerformedAtAscIdAsc(Long groupId,
            Collection<PerformanceStatus> statuses, Pageable pageable);

    // 지난 공연(period=PAST) — 공연일 최근 순. 공연일이 같으면 나중에 만든 공연이 먼저다.
    Page<Performance> findByGroupIdAndStatusInOrderByPerformedAtDescIdDesc(Long groupId,
            Collection<PerformanceStatus> statuses, Pageable pageable);
}

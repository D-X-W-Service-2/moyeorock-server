package com.moyeorock.domain.performance.repository;

import com.moyeorock.domain.performance.entity.Performance;
import com.moyeorock.domain.performance.enums.PerformanceStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PerformanceRepository extends JpaRepository<Performance, Long> {

    /**
     * 공연 행을 잠그고 읽는다(SELECT ... FOR UPDATE). 셋리스트 넘기기와 모임장의 확정·해제가 같은 공연에서 겹치면
     * 뒤에 온 요청이 앞 요청의 커밋까지 기다려 차례로 처리된다 — 확정된 셋리스트에 넘기거나, 넘겨진 곡을 못 본 채 확정되지 않는다.
     * 잠금 조회는 REPEATABLE READ 스냅샷이 아니라 커밋된 최신 행을 읽는다(PerformanceSetlistConcurrencyTest, 이슈 #103·PR #111 리뷰).
     * 트랜잭션의 첫 조회로 불러야 이후 조회가 잠금 뒤의 상태를 본다(GroupRepository.findByIdAndStatusForUpdate와 같은 방식).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Performance p WHERE p.id = :id")
    Optional<Performance> findByIdForUpdate(@Param("id") Long id);

    // 모임 공연 목록 — 공연일 늦은 순. 공연일이 같은 공연이 페이지 경계에 걸려도 중복·누락이 없게 끝에 id를 붙인다.
    Page<Performance> findByGroupIdOrderByPerformedAtDescIdDesc(Long groupId, Pageable pageable);

    // 다가올 공연(period=UPCOMING) — 공연일 가까운 순. 공연일이 같으면 먼저 만든 공연이 먼저다.
    Page<Performance> findByGroupIdAndStatusInOrderByPerformedAtAscIdAsc(Long groupId,
            Collection<PerformanceStatus> statuses, Pageable pageable);

    // 동아리 홈의 다가오는 공연 — 위와 같은 기준·정렬로 앞의 3건(고정 공지 미리보기와 같은 개수). 개수 쿼리가 없게 Page를 쓰지 않는다
    List<Performance> findTop3ByGroupIdAndStatusInOrderByPerformedAtAscIdAsc(Long groupId,
            Collection<PerformanceStatus> statuses);

    // 지난 공연(period=PAST) — 공연일 최근 순. 공연일이 같으면 나중에 만든 공연이 먼저다.
    Page<Performance> findByGroupIdAndStatusInOrderByPerformedAtDescIdDesc(Long groupId,
            Collection<PerformanceStatus> statuses, Pageable pageable);
}

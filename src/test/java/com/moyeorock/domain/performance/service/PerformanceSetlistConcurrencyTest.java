package com.moyeorock.domain.performance.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.domain.performance.entity.Performance;
import com.moyeorock.domain.performance.repository.PerformanceRepository;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 셋리스트 넘기기·확정·해제가 잡는 공연 행 잠금(PerformanceRepository.findByIdForUpdate)이 실제 MySQL에서 줄을 세우고,
 * 뒤 요청이 앞 요청의 커밋 뒤 상태를 읽는지 확인한다(이슈 #103, PR #111 리뷰로 낙관적 락에서 바꿈).
 * REPEATABLE READ의 일반 조회는 트랜잭션 스냅샷을 읽지만 잠금 조회는 커밋된 최신 행을 읽어야 한다 — 그 회귀를 막는 테스트다.
 *
 * <p>두 요청이 따로 커밋되어야 엇갈릴 수 있으므로 클래스에 {@code @Transactional}을 붙이지 않고,
 * 테스트가 끝나면 남은 행을 직접 지운다(TeamConcurrencyTest와 같은 방식).
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
class PerformanceSetlistConcurrencyTest {

    // 앞 요청이 잠금을 쥔 채 머무는 시간 — 뒤 요청이 정말 기다리는지 보려면 이보다 늦게 잠금을 얻어야 한다
    private static final long HOLD_MILLIS = 500;

    @Autowired
    private PerformanceRepository performanceRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUp() {
        performanceRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("확정이 공연 행을 쥐고 있으면 넘기기의 잠금 조회는 확정 커밋까지 기다렸다가, 스냅샷이 아니라 확정된 상태를 읽는다")
    void submitWaitsForConfirm_andSeesConfirmedState() throws Exception {
        Long performanceId = savePerformance();
        CountDownLatch confirmLocked = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> confirm = pool.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                Performance performance = performanceRepository.findByIdForUpdate(performanceId).orElseThrow();
                confirmLocked.countDown();
                sleep(HOLD_MILLIS);
                performance.confirmSetlist(LocalDateTime.now());
            }));
            confirmLocked.await(10, TimeUnit.SECONDS);

            Boolean confirmedSeenBySubmit = new TransactionTemplate(transactionManager).execute(status -> {
                // 일반 조회로 먼저 스냅샷을 만들어 둔다 — 잠금 조회가 이 스냅샷이 아닌 최신 행을 읽는지 보려는 것
                performanceRepository.count();
                return performanceRepository.findByIdForUpdate(performanceId).orElseThrow().isSetlistConfirmed();
            });

            confirm.get(10, TimeUnit.SECONDS);
            assertThat(confirmedSeenBySubmit).isTrue();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("넘기기가 공연 행을 쥐고 있으면 확정의 잠금 조회는 넘기기가 커밋할 때까지 기다린다 — 넘겨진 곡을 못 본 채 확정되지 않는다")
    void confirmWaitsForSubmitCommit() throws Exception {
        Long performanceId = savePerformance();
        CountDownLatch submitLocked = new CountDownLatch(1);
        AtomicLong submitCommitStartedAt = new AtomicLong();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> submit = pool.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                performanceRepository.findByIdForUpdate(performanceId).orElseThrow();
                submitLocked.countDown();
                sleep(HOLD_MILLIS);
                // 이 시각 뒤에 커밋된다 — 확정이 잠금을 얻은 시각은 이보다 늦어야 한다
                submitCommitStartedAt.set(System.nanoTime());
            }));
            submitLocked.await(10, TimeUnit.SECONDS);

            Long confirmLockedAt = new TransactionTemplate(transactionManager).execute(status -> {
                performanceRepository.findByIdForUpdate(performanceId).orElseThrow();
                return System.nanoTime();
            });

            submit.get(10, TimeUnit.SECONDS);
            assertThat(confirmLockedAt).isGreaterThan(submitCommitStartedAt.get());
        } finally {
            pool.shutdownNow();
        }
    }

    private Long savePerformance() {
        return performanceRepository.save(Performance.create(1L, 10L, "2026 정기공연", null,
                LocalDateTime.now().plusMonths(1), "홍대 롤링홀", null)).getId();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}

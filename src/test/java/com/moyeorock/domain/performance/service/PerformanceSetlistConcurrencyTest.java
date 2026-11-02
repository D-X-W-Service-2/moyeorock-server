package com.moyeorock.domain.performance.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.domain.performance.entity.Performance;
import com.moyeorock.domain.performance.repository.PerformanceRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 셋리스트 넘기기의 강제 증가 낙관적 락이 실제 MySQL에서 "읽은 뒤 공연이 바뀐" 경우를 잡는지, 거꾸로 확정이
 * 읽은 뒤 넘기기가 먼저 커밋된 경우도 잡는지 확인한다(이슈 #103).
 * 일반 OPTIMISTIC은 REPEATABLE READ 스냅샷 때문에 이 경우를 못 잡아서 강제 증가로 바꿨다 — 그 회귀를 막는 테스트다.
 *
 * <p>넘기기와 확정·해제가 따로 커밋되어야 엇갈릴 수 있으므로 클래스에 {@code @Transactional}을 붙이지 않고,
 * 테스트가 끝나면 남은 행을 직접 지운다(TeamConcurrencyTest와 같은 방식).
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
class PerformanceSetlistConcurrencyTest {

    @Autowired
    private PerformanceRepository performanceRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUp() {
        performanceRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("넘기기가 공연을 읽은 뒤 다른 요청(확정·해제 등)이 먼저 공연을 고쳐 커밋하면, 넘기기는 커밋 때 낙관적 락 실패다")
    void submitFails_whenPerformanceChangedAfterRead() {
        Long performanceId = savePerformance();
        TransactionTemplate submit = new TransactionTemplate(transactionManager);
        TransactionTemplate confirm = new TransactionTemplate(transactionManager);
        confirm.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThatThrownBy(() -> submit.executeWithoutResult(status -> {
            performanceRepository.findByIdForSetlistSubmit(performanceId).orElseThrow();
            confirm.executeWithoutResult(inner -> performanceRepository.findById(performanceId).orElseThrow()
                    .confirmSetlist(LocalDateTime.now()));
        })).isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("확정이 공연을 읽은 뒤 넘기기가 먼저 커밋하면, 확정은 커밋 때 낙관적 락 실패다 — 넘겨진 곡을 못 본 채 확정되지 않는다")
    void confirmFails_whenSubmitCommittedAfterRead() {
        Long performanceId = savePerformance();
        TransactionTemplate confirm = new TransactionTemplate(transactionManager);
        TransactionTemplate submit = new TransactionTemplate(transactionManager);
        submit.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThatThrownBy(() -> confirm.executeWithoutResult(status -> {
            Performance performance = performanceRepository.findById(performanceId).orElseThrow();
            submit.executeWithoutResult(inner -> performanceRepository.findByIdForSetlistSubmit(performanceId).orElseThrow());
            performance.confirmSetlist(LocalDateTime.now());
        })).isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("그사이 아무도 공연을 고치지 않았으면 넘기기 쪽 커밋은 성공한다")
    void submitCommits_whenPerformanceUnchanged() {
        Long performanceId = savePerformance();
        TransactionTemplate submit = new TransactionTemplate(transactionManager);

        assertThatCode(() -> submit.executeWithoutResult(status ->
                performanceRepository.findByIdForSetlistSubmit(performanceId).orElseThrow()))
                .doesNotThrowAnyException();
    }

    private Long savePerformance() {
        return performanceRepository.save(Performance.create(1L, 10L, "2026 정기공연", null,
                LocalDateTime.now().plusMonths(1), "홍대 롤링홀", null)).getId();
    }
}

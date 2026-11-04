package com.moyeorock.domain.performance.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.domain.performance.entity.Performance;
import com.moyeorock.domain.performance.enums.PerformancePeriod;
import com.moyeorock.domain.performance.enums.PerformanceStatus;
import com.moyeorock.global.config.JpaAuditingConfig;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

/**
 * 모임 공연 목록의 필터·정렬·페이징을 실제 MySQL에서 확인한다. 정렬을 메서드 이름에 고정했기 때문에
 * Service는 정렬 없는 PageRequest만 넘긴다 — 그 정렬이 실제 SQL로 지켜지는지가 이 테스트의 핵심이다.
 */
@DataJpaTest
@Import({JpaAuditingConfig.class, TestcontainersConfig.class})
class PerformanceRepositoryTest {

    private static final Long GROUP_ID = 1L;
    private static final Long OTHER_GROUP_ID = 2L;

    @Autowired
    private PerformanceRepository performanceRepository;

    @Test
    @DisplayName("모임 공연 목록은 그 모임 공연만 공연일 늦은 순으로 주고, 공연일이 같으면 나중에 만든 공연이 먼저다")
    void findByGroupId_filtersByGroup_andOrdersByPerformedAtDescThenIdDesc() {
        save(GROUP_ID, "봄 공연", LocalDateTime.of(2026, 3, 1, 18, 0));
        save(GROUP_ID, "가을 공연 1부", LocalDateTime.of(2026, 10, 1, 18, 0));
        save(GROUP_ID, "가을 공연 2부", LocalDateTime.of(2026, 10, 1, 18, 0));
        save(OTHER_GROUP_ID, "다른 모임 공연", LocalDateTime.of(2026, 12, 1, 18, 0));

        Page<Performance> page = performanceRepository.findByGroupIdOrderByPerformedAtDescIdDesc(
                GROUP_ID, PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Performance::getTitle)
                .containsExactly("가을 공연 2부", "가을 공연 1부", "봄 공연");
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("페이지를 나눠도 정렬 순서대로 이어지고 전체 개수·마지막 여부가 맞다")
    void findByGroupId_pagesInOrder() {
        save(GROUP_ID, "1월 공연", LocalDateTime.of(2026, 1, 1, 18, 0));
        save(GROUP_ID, "2월 공연", LocalDateTime.of(2026, 2, 1, 18, 0));
        save(GROUP_ID, "3월 공연", LocalDateTime.of(2026, 3, 1, 18, 0));

        Page<Performance> first = performanceRepository.findByGroupIdOrderByPerformedAtDescIdDesc(
                GROUP_ID, PageRequest.of(0, 2));
        Page<Performance> second = performanceRepository.findByGroupIdOrderByPerformedAtDescIdDesc(
                GROUP_ID, PageRequest.of(1, 2));

        assertThat(first.getContent()).extracting(Performance::getTitle).containsExactly("3월 공연", "2월 공연");
        assertThat(first.isLast()).isFalse();
        assertThat(second.getContent()).extracting(Performance::getTitle).containsExactly("1월 공연");
        assertThat(second.isLast()).isTrue();
        assertThat(second.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("다가올 공연은 그 모임의 PLANNED·RECRUITING만 공연일 가까운 순으로 주고, 공연일이 같으면 먼저 만든 공연이 먼저다")
    void findUpcoming_filtersStatuses_andOrdersByPerformedAtAscThenIdAsc() {
        save(GROUP_ID, "겨울 공연", LocalDateTime.of(2026, 12, 1, 18, 0), PerformanceStatus.PLANNED);
        save(GROUP_ID, "가을 공연 1부", LocalDateTime.of(2026, 10, 1, 18, 0), PerformanceStatus.RECRUITING);
        save(GROUP_ID, "가을 공연 2부", LocalDateTime.of(2026, 10, 1, 18, 0), PerformanceStatus.PLANNED);
        save(GROUP_ID, "끝난 공연", LocalDateTime.of(2026, 11, 1, 18, 0), PerformanceStatus.DONE);
        save(GROUP_ID, "취소된 공연", LocalDateTime.of(2026, 11, 15, 18, 0), PerformanceStatus.CANCELED);
        save(OTHER_GROUP_ID, "다른 모임 공연", LocalDateTime.of(2026, 11, 1, 18, 0), PerformanceStatus.PLANNED);

        Page<Performance> page = performanceRepository.findByGroupIdAndStatusInOrderByPerformedAtAscIdAsc(
                GROUP_ID, PerformancePeriod.UPCOMING.getStatuses(), PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Performance::getTitle)
                .containsExactly("가을 공연 1부", "가을 공연 2부", "겨울 공연");
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("동아리 홈의 다가오는 공연은 다가올 공연과 같은 기준·순서로 앞의 3건만 준다")
    void findTop3Upcoming_limitsToThree() {
        save(GROUP_ID, "12월 공연", LocalDateTime.of(2026, 12, 1, 18, 0), PerformanceStatus.PLANNED);
        save(GROUP_ID, "9월 공연", LocalDateTime.of(2026, 9, 1, 18, 0), PerformanceStatus.RECRUITING);
        save(GROUP_ID, "10월 공연", LocalDateTime.of(2026, 10, 1, 18, 0), PerformanceStatus.PLANNED);
        save(GROUP_ID, "11월 공연", LocalDateTime.of(2026, 11, 1, 18, 0), PerformanceStatus.PLANNED);
        save(GROUP_ID, "끝난 공연", LocalDateTime.of(2026, 8, 1, 18, 0), PerformanceStatus.DONE);
        save(OTHER_GROUP_ID, "다른 모임 공연", LocalDateTime.of(2026, 8, 15, 18, 0), PerformanceStatus.PLANNED);

        assertThat(performanceRepository.findTop3ByGroupIdAndStatusInOrderByPerformedAtAscIdAsc(
                GROUP_ID, PerformancePeriod.UPCOMING.getStatuses()))
                .extracting(Performance::getTitle)
                .containsExactly("9월 공연", "10월 공연", "11월 공연");
    }

    @Test
    @DisplayName("지난 공연은 그 모임의 DONE·CANCELED만 공연일 최근 순으로 주고, 공연일이 같으면 나중에 만든 공연이 먼저다")
    void findPast_filtersStatuses_andOrdersByPerformedAtDescThenIdDesc() {
        save(GROUP_ID, "봄 공연", LocalDateTime.of(2026, 3, 1, 18, 0), PerformanceStatus.DONE);
        save(GROUP_ID, "가을 공연 1부", LocalDateTime.of(2026, 10, 1, 18, 0), PerformanceStatus.DONE);
        save(GROUP_ID, "가을 공연 2부", LocalDateTime.of(2026, 10, 1, 18, 0), PerformanceStatus.CANCELED);
        save(GROUP_ID, "겨울 공연", LocalDateTime.of(2026, 12, 1, 18, 0), PerformanceStatus.PLANNED);
        save(OTHER_GROUP_ID, "다른 모임 공연", LocalDateTime.of(2026, 11, 1, 18, 0), PerformanceStatus.DONE);

        Page<Performance> page = performanceRepository.findByGroupIdAndStatusInOrderByPerformedAtDescIdDesc(
                GROUP_ID, PerformancePeriod.PAST.getStatuses(), PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Performance::getTitle)
                .containsExactly("가을 공연 2부", "가을 공연 1부", "봄 공연");
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("내 활동 공연은 주어진 id 중 주어진 상태만 공연일 가까운 순으로 주고, 공연일이 같으면 먼저 만든 공연이 먼저다")
    void findByIdIn_filtersIdsAndStatuses_andOrdersByPerformedAtAscThenIdAsc() {
        Performance winter = save(GROUP_ID, "겨울 공연", LocalDateTime.of(2026, 12, 1, 18, 0), PerformanceStatus.PLANNED);
        Performance autumn1 = save(OTHER_GROUP_ID, "가을 공연 1부", LocalDateTime.of(2026, 10, 1, 18, 0),
                PerformanceStatus.RECRUITING);
        Performance autumn2 = save(GROUP_ID, "가을 공연 2부", LocalDateTime.of(2026, 10, 1, 18, 0),
                PerformanceStatus.PLANNED);
        Performance done = save(GROUP_ID, "끝난 공연", LocalDateTime.of(2026, 9, 1, 18, 0), PerformanceStatus.DONE);
        save(GROUP_ID, "내 팀이 없는 공연", LocalDateTime.of(2026, 11, 1, 18, 0), PerformanceStatus.PLANNED);

        List<Performance> performances = performanceRepository.findByIdInAndStatusInOrderByPerformedAtAscIdAsc(
                List.of(winter.getId(), autumn1.getId(), autumn2.getId(), done.getId()),
                PerformancePeriod.UPCOMING.getStatuses());

        assertThat(performances).extracting(Performance::getTitle)
                .containsExactly("가을 공연 1부", "가을 공연 2부", "겨울 공연");
    }

    private Performance save(Long groupId, String title, LocalDateTime performedAt) {
        return save(groupId, title, performedAt, PerformanceStatus.PLANNED);
    }

    private Performance save(Long groupId, String title, LocalDateTime performedAt, PerformanceStatus status) {
        Performance performance = Performance.create(groupId, 10L, title, null, performedAt, "홍대 롤링홀", null);
        performance.changeStatus(status);
        return performanceRepository.save(performance);
    }
}

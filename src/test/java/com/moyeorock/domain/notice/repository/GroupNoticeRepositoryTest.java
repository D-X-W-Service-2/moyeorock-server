package com.moyeorock.domain.notice.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.domain.notice.entity.GroupNotice;
import com.moyeorock.global.config.JpaAuditingConfig;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

/**
 * 고정 공지 우선 정렬과 미리보기 3건 제한을 실제 MySQL에서 검증한다.
 * {@code @DataJpaTest}는 {@code @Configuration}을 자동 로드하지 않아 auditing을 명시적으로 켠다
 * ({@code GroupNotice}가 {@code BaseEntity}를 상속해 created_at·updated_at이 NOT NULL이다).
 */
@DataJpaTest
@Import({JpaAuditingConfig.class, TestcontainersConfig.class})
class GroupNoticeRepositoryTest {

    private static final Long GROUP_ID = 1L;
    private static final Long AUTHOR_ID = 10L;

    @Autowired
    private GroupNoticeRepository groupNoticeRepository;

    @Test
    @DisplayName("공지 목록은 고정 공지를 먼저 내려준다")
    void findByGroupId_putsPinnedFirst() {
        groupNoticeRepository.saveAll(List.of(
                notice("일반 공지 1", false),
                notice("고정 공지", true),
                notice("일반 공지 2", false)));

        List<GroupNotice> notices = groupNoticeRepository
                .findByGroupIdOrderByIsPinnedDescCreatedAtDesc(GROUP_ID, PageRequest.of(0, 20))
                .getContent();

        assertThat(notices).hasSize(3);
        assertThat(notices.get(0).getTitle()).isEqualTo("고정 공지");
        assertThat(notices.get(0).isPinned()).isTrue();
    }

    @Test
    @DisplayName("isPinned=true로 조회하면 고정 공지만 나온다")
    void findByGroupIdAndIsPinned_returnsOnlyPinned() {
        groupNoticeRepository.saveAll(List.of(
                notice("일반 공지", false), notice("고정 공지", true)));

        var page = groupNoticeRepository
                .findByGroupIdAndIsPinnedOrderByCreatedAtDesc(GROUP_ID, true, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getTitle()).isEqualTo("고정 공지");
    }

    @Test
    @DisplayName("동아리 홈 미리보기는 고정 공지 우선으로 최대 3건만 가져온다")
    void findTop3_limitsToThreeWithPinnedFirst() {
        groupNoticeRepository.saveAll(List.of(
                notice("일반 1", false), notice("일반 2", false), notice("일반 3", false),
                notice("고정 1", true), notice("고정 2", true)));

        List<GroupNotice> preview =
                groupNoticeRepository.findTop3ByGroupIdOrderByIsPinnedDescCreatedAtDesc(GROUP_ID);

        assertThat(preview).hasSize(3);
        assertThat(preview).filteredOn(GroupNotice::isPinned).hasSize(2);
    }

    @Test
    @DisplayName("다른 모임의 공지는 섞이지 않는다")
    void findByGroupId_isScopedToGroup() {
        groupNoticeRepository.saveAll(List.of(
                notice("우리 모임 공지", false),
                GroupNotice.create(99L, AUTHOR_ID, "다른 모임 공지", "본문", false)));

        var page = groupNoticeRepository
                .findByGroupIdOrderByIsPinnedDescCreatedAtDesc(GROUP_ID, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getTitle()).isEqualTo("우리 모임 공지");
    }

    private static GroupNotice notice(String title, boolean pinned) {
        return GroupNotice.create(GROUP_ID, AUTHOR_ID, title, "본문", pinned);
    }
}

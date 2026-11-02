package com.moyeorock.domain.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.domain.notification.entity.Notification;
import com.moyeorock.global.common.enums.NotificationTargetType;
import com.moyeorock.global.common.enums.NotificationType;
import com.moyeorock.global.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/** 읽음 일괄 갱신·집계·정렬을 실제 MySQL에서 확인한다. {@code @DataJpaTest}는 auditing을 자동으로 켜지 않아 가져온다. */
@DataJpaTest
@Import({JpaAuditingConfig.class, TestcontainersConfig.class})
class NotificationRepositoryTest {

    private static final Long ME = 1L;
    private static final Long OTHER = 2L;
    private static final Sort LATEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private EntityManager entityManager;

    private Notification save(Long userId, String message) {
        Notification saved = notificationRepository.saveAndFlush(Notification.create(
                userId, NotificationType.TEAM_INVITE, message, NotificationTargetType.INVITATION, 91L));
        return saved;
    }

    private boolean isRead(Notification notification) {
        entityManager.flush();
        entityManager.clear();
        return notificationRepository.findById(notification.getId()).orElseThrow().isRead();
    }

    @Test
    @DisplayName("목록은 본인 알림만, 최신순(createdAt → id 내림차순)으로 페이징해 돌려준다")
    void findByUserId_ownOnly_latestFirst() {
        Notification first = save(ME, "첫째");
        Notification second = save(ME, "둘째");
        Notification third = save(ME, "셋째");
        save(OTHER, "남의 알림");

        Page<Notification> page = notificationRepository.findByUserId(ME, PageRequest.of(0, 2, LATEST_FIRST));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).extracting(Notification::getId).containsExactly(third.getId(), second.getId());
        Page<Notification> next = notificationRepository.findByUserId(ME, PageRequest.of(1, 2, LATEST_FIRST));
        assertThat(next.getContent()).extracting(Notification::getId).containsExactly(first.getId());
        assertThat(next.isLast()).isTrue();
    }

    @Test
    @DisplayName("안읽은 수는 본인의 읽지 않은 알림만 센다")
    void countUnread_ownOnly() {
        Notification read = save(ME, "읽음");
        save(ME, "안읽음1");
        save(ME, "안읽음2");
        save(OTHER, "남의 안읽음");
        read.markAsRead();
        entityManager.flush();

        assertThat(notificationRepository.countByUserIdAndIsReadFalse(ME)).isEqualTo(2);
        assertThat(notificationRepository.countByUserIdAndIsReadFalse(OTHER)).isEqualTo(1);
    }

    @Test
    @DisplayName("전체 읽음은 본인의 안읽은 알림만 처리하고 건수를 돌려준다 (이미 읽은 것과 남의 알림은 그대로)")
    void markAllAsRead_ownUnreadOnly() {
        Notification already = save(ME, "이미 읽음");
        already.markAsRead();
        Notification unread1 = save(ME, "안읽음1");
        Notification unread2 = save(ME, "안읽음2");
        Notification others = save(OTHER, "남의 알림");
        entityManager.flush();

        int count = notificationRepository.markAllAsRead(ME);

        assertThat(count).isEqualTo(2);
        assertThat(isRead(unread1)).isTrue();
        assertThat(isRead(unread2)).isTrue();
        assertThat(isRead(others)).isFalse();
    }

    @Test
    @DisplayName("지정 읽음은 지정한 id 중 본인의 안읽은 알림만 처리한다 (남의 알림·없는 id·이미 읽은 알림은 건수에 안 잡힌다)")
    void markAsRead_selectedOwnUnreadOnly() {
        Notification target = save(ME, "대상");
        Notification notSelected = save(ME, "선택 안 함");
        Notification already = save(ME, "이미 읽음");
        already.markAsRead();
        Notification others = save(OTHER, "남의 알림");
        entityManager.flush();

        int count = notificationRepository.markAsRead(ME,
                Set.of(target.getId(), already.getId(), others.getId(), 999_999L));

        assertThat(count).isEqualTo(1);
        assertThat(isRead(target)).isTrue();
        assertThat(isRead(notSelected)).isFalse();
        assertThat(isRead(others)).isFalse();
    }

    @Test
    @DisplayName("255자 메시지도 저장·조회된다 (컬럼 길이 경계)")
    void message_atColumnLimit() {
        String message = "가".repeat(255);
        Notification saved = save(ME, message);

        entityManager.clear();

        assertThat(notificationRepository.findById(saved.getId()).orElseThrow().getMessage()).hasSize(255);
        assertThat(List.of(saved.getMessage())).containsExactly(message);
    }
}

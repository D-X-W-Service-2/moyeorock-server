package com.moyeorock.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.domain.notification.entity.Notification;
import com.moyeorock.domain.notification.repository.NotificationRepository;
import com.moyeorock.global.common.enums.NotificationTargetType;
import com.moyeorock.global.common.enums.NotificationType;
import com.moyeorock.global.event.NotificationEvent;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이벤트 발행 → 저장 흐름을 실제 트랜잭션 경계로 확인한다. 클래스 단위 {@code @Transactional}을 쓰면 AFTER_COMMIT이
 * 호출되지 않으므로 쓰지 않고, 테스트마다 직접 지운다.
 */
@SpringBootTest
@Import({TestcontainersConfig.class, NotificationEventTest.PublisherProbe.class})
class NotificationEventTest {

    private static final Long USER_ID = 4_242L;

    /** 발행하는 쪽(예: 가입 승인)을 흉내 낸다. */
    @TestConfiguration
    static class PublisherProbe {

        @Component
        static class Publisher {

            private final ApplicationEventPublisher publisher;

            Publisher(ApplicationEventPublisher publisher) {
                this.publisher = publisher;
            }

            @Transactional
            public void publishAndCommit(NotificationEvent event) {
                publisher.publishEvent(event);
            }

            @Transactional
            public void publishThenFail(NotificationEvent event) {
                publisher.publishEvent(event);
                throw new IllegalStateException("핵심 작업 실패");
            }

            public void publishWithoutTransaction(NotificationEvent event) {
                publisher.publishEvent(event);
            }
        }
    }

    @Autowired
    private PublisherProbe.Publisher publisher;

    @MockitoSpyBean
    private NotificationRepository notificationRepository;

    @AfterEach
    void cleanUp() {
        notificationRepository.deleteAllInBatch();
    }

    private static NotificationEvent event(String message) {
        return new NotificationEvent(USER_ID, NotificationType.JOIN_REQUEST_APPROVED, message,
                NotificationTargetType.JOIN_REQUEST, 77L);
    }

    private List<Notification> saved() {
        return notificationRepository.findAll().stream().filter(n -> USER_ID.equals(n.getUserId())).toList();
    }

    @Test
    @DisplayName("발행한 쪽이 커밋되면 알림이 저장된다")
    void commit_savesNotification() {
        publisher.publishAndCommit(event("가입 신청이 승인되었습니다."));

        assertThat(saved()).singleElement().satisfies(n -> {
            assertThat(n.getMessage()).isEqualTo("가입 신청이 승인되었습니다.");
            assertThat(n.getType()).isEqualTo(NotificationType.JOIN_REQUEST_APPROVED);
            assertThat(n.getTargetType()).isEqualTo(NotificationTargetType.JOIN_REQUEST);
            assertThat(n.getTargetId()).isEqualTo(77L);
            assertThat(n.isRead()).isFalse();
            assertThat(n.getCreatedAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("발행한 쪽이 롤백되면 알림은 저장되지 않는다 (없었던 일의 알림이 남지 않는다)")
    void rollback_savesNothing() {
        try {
            publisher.publishThenFail(event("저장되면 안 되는 알림"));
        } catch (IllegalStateException expected) {
            // 핵심 작업 실패 → 롤백
        }

        assertThat(saved()).isEmpty();
    }

    @Test
    @DisplayName("알림 저장이 실패해도 발행한 쪽은 예외 없이 끝난다 (알림만 유실)")
    void listenerFailure_doesNotAffectPublisher() {
        doThrow(new IllegalStateException("db down")).when(notificationRepository).save(any());

        publisher.publishAndCommit(event("유실될 알림"));

        assertThat(saved()).isEmpty();
    }

    @Test
    @DisplayName("트랜잭션 밖에서 발행하면 이벤트가 버려져 알림이 저장되지 않는다 — 반드시 트랜잭션 안에서 발행해야 한다")
    void withoutTransaction_isDropped() {
        publisher.publishWithoutTransaction(event("버려지는 알림"));

        assertThat(saved()).isEmpty();
    }

    @Test
    @DisplayName("255자를 넘는 메시지는 이벤트 생성 시 잘려 저장된다 (저장 실패로 유실되지 않는다)")
    void longMessage_isTruncatedNotLost() {
        publisher.publishAndCommit(event("가".repeat(300)));

        assertThat(saved()).singleElement()
                .satisfies(n -> assertThat(n.getMessage()).hasSize(NotificationEvent.MESSAGE_MAX_LENGTH));
    }

    @Test
    @DisplayName("필수 값이 빠진 이벤트는 저장하지 않지만 발행한 쪽은 영향받지 않는다")
    void incompleteEvent_isSkipped() {
        publisher.publishAndCommit(new NotificationEvent(USER_ID, null, "타입 없음",
                NotificationTargetType.TEAM, 1L));

        assertThat(saved()).isEmpty();
    }
}

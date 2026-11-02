package com.moyeorock.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.moyeorock.domain.notification.dto.request.NotificationReadRequest;
import com.moyeorock.domain.notification.dto.response.NotificationReadResponse;
import com.moyeorock.domain.notification.dto.response.NotificationsResponse;
import com.moyeorock.domain.notification.entity.Notification;
import com.moyeorock.domain.notification.repository.NotificationRepository;
import com.moyeorock.global.common.enums.NotificationTargetType;
import com.moyeorock.global.common.enums.NotificationType;
import com.moyeorock.global.event.NotificationEvent;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    private static Notification notification(String message) {
        return Notification.create(USER_ID, NotificationType.TEAM_INVITE, message, NotificationTargetType.INVITATION, 91L);
    }

    private static NotificationEvent event() {
        return new NotificationEvent(USER_ID, NotificationType.TEAM_APPLY, "신청이 도착했습니다.",
                NotificationTargetType.JOIN_REQUEST, 7L);
    }

    @Test
    @DisplayName("목록은 클라이언트 sort를 무시하고 최신순(createdAt, id 내림차순)으로 조회하고 안읽은 수를 함께 돌려준다")
    void getNotifications_ignoresClientSort_andIncludesUnreadCount() {
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        Page<Notification> page = new PageImpl<>(List.of(notification("첫째")), PageRequest.of(2, 5), 11);
        given(notificationRepository.findByUserId(any(), pageable.capture())).willReturn(page);
        given(notificationRepository.countByUserIdAndIsReadFalse(USER_ID)).willReturn(3);

        NotificationsResponse response = notificationService.getNotifications(
                USER_ID, PageRequest.of(2, 5, Sort.by("bogus")));

        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        assertThat(response.unreadCount()).isEqualTo(3);
        assertThat(response.notifications().totalElements()).isEqualTo(11);
        assertThat(response.notifications().content()).singleElement()
                .satisfies(item -> assertThat(item.message()).isEqualTo("첫째"));
    }

    @Test
    @DisplayName("ids를 지정하면 중복을 뺀 id로 본인 알림만 읽음 처리하고 처리 건수와 남은 안읽은 수를 돌려준다")
    void markAsRead_withIds() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> ids = ArgumentCaptor.forClass(Collection.class);
        given(notificationRepository.markAsRead(org.mockito.ArgumentMatchers.eq(USER_ID), ids.capture())).willReturn(2);
        given(notificationRepository.countByUserIdAndIsReadFalse(USER_ID)).willReturn(1);

        NotificationReadResponse response = notificationService.markAsRead(
                USER_ID, new NotificationReadRequest(List.of(10L, 20L, 10L)));

        assertThat(ids.getValue()).containsExactlyInAnyOrder(10L, 20L);
        assertThat(response).isEqualTo(new NotificationReadResponse(2, 1));
        verify(notificationRepository, never()).markAllAsRead(any());
    }

    @Test
    @DisplayName("ids가 빈 배열·null이거나 요청 바디가 없으면 전체 읽음이다")
    void markAsRead_allWhenIdsEmptyOrMissing() {
        given(notificationRepository.markAllAsRead(USER_ID)).willReturn(4, 4, 4);
        given(notificationRepository.countByUserIdAndIsReadFalse(USER_ID)).willReturn(0);

        assertThat(notificationService.markAsRead(USER_ID, new NotificationReadRequest(List.of())))
                .isEqualTo(new NotificationReadResponse(4, 0));
        assertThat(notificationService.markAsRead(USER_ID, new NotificationReadRequest(null)))
                .isEqualTo(new NotificationReadResponse(4, 0));
        assertThat(notificationService.markAsRead(USER_ID, null))
                .isEqualTo(new NotificationReadResponse(4, 0));
        verify(notificationRepository, never()).markAsRead(any(), any());
    }

    @Test
    @DisplayName("이벤트를 받으면 안읽음 상태의 알림을 저장한다")
    void saveOnEvent_savesUnreadNotification() {
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);

        notificationService.saveOnEvent(event());

        verify(notificationRepository).save(saved.capture());
        Notification notification = saved.getValue();
        assertThat(notification.getUserId()).isEqualTo(USER_ID);
        assertThat(notification.getType()).isEqualTo(NotificationType.TEAM_APPLY);
        assertThat(notification.getMessage()).isEqualTo("신청이 도착했습니다.");
        assertThat(notification.getTargetType()).isEqualTo(NotificationTargetType.JOIN_REQUEST);
        assertThat(notification.getTargetId()).isEqualTo(7L);
        assertThat(notification.isRead()).isFalse();
    }

    @Test
    @DisplayName("필수 값이 빠진 이벤트는 저장하지 않고 예외도 던지지 않는다")
    void saveOnEvent_skipsIncompleteEvent() {
        notificationService.saveOnEvent(new NotificationEvent(null, NotificationType.TEAM_APPLY, "m",
                NotificationTargetType.TEAM, 1L));
        notificationService.saveOnEvent(new NotificationEvent(USER_ID, NotificationType.TEAM_APPLY, null,
                NotificationTargetType.TEAM, 1L));

        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("저장이 실패하면 예외를 그대로 다시 던진다 (발행한 쪽 영향 차단은 AFTER_COMMIT이 맡는다)")
    void saveOnEvent_rethrowsOnFailure() {
        given(notificationRepository.save(any())).willThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> notificationService.saveOnEvent(event()))
                .isInstanceOf(IllegalStateException.class);
    }
}

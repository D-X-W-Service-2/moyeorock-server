package com.moyeorock.domain.notification.dto.response;

import com.moyeorock.domain.notification.entity.Notification;
import com.moyeorock.global.common.dto.PageResponse;
import org.springframework.data.domain.Page;

/** 배지 숫자(unreadCount) 때문에 래퍼를 두지만, 알림은 상한이 없어 목록 자체는 페이징한다(api-conventions.md §3). */
public record NotificationsResponse(int unreadCount, PageResponse<NotificationResponse> notifications) {

    public static NotificationsResponse of(int unreadCount, Page<Notification> page) {
        return new NotificationsResponse(unreadCount, PageResponse.from(page.map(NotificationResponse::from)));
    }
}

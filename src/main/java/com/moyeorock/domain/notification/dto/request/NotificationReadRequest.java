package com.moyeorock.domain.notification.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * PATCH /v1/notifications/read. {@code ids}가 null이거나 빈 배열이면 전체 읽음이다(명세: "빈 배열이면 전체 읽음 처리").
 * 한 번에 지정할 수 있는 id는 {@value #MAX_IDS}개까지다 — 명세에 없는 서버 정책이다.
 */
public record NotificationReadRequest(
        @Size(max = MAX_IDS, message = "한 번에 읽음 처리할 수 있는 알림은 100개까지입니다.")
        List<@NotNull Long> ids
) {

    public static final int MAX_IDS = 100;
}

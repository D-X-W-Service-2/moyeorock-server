package com.moyeorock.domain.notification.controller;

import com.moyeorock.domain.notification.dto.request.NotificationReadRequest;
import com.moyeorock.domain.notification.dto.response.NotificationReadResponse;
import com.moyeorock.domain.notification.dto.response.NotificationsResponse;
import com.moyeorock.domain.notification.service.NotificationService;
import com.moyeorock.global.common.dto.ApiResponse;
import com.moyeorock.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<NotificationsResponse> getNotifications(@AuthUser Long userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(notificationService.getNotifications(userId, pageable));
    }

    /** 바디가 없거나 {@code ids}가 비어 있으면 전체 읽음이다. */
    @PatchMapping("/read")
    public ApiResponse<NotificationReadResponse> markAsRead(@AuthUser Long userId,
            @Valid @RequestBody(required = false) NotificationReadRequest request) {
        return ApiResponse.success(notificationService.markAsRead(userId, request));
    }
}

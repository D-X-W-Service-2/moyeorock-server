package com.moyeorock.domain.join.controller;

import com.moyeorock.domain.join.dto.request.JoinRequestCreateRequest;
import com.moyeorock.domain.join.dto.request.JoinRequestStatusUpdateRequest;
import com.moyeorock.domain.join.dto.response.JoinRequestResponse;
import com.moyeorock.domain.join.dto.response.JoinResultResponse;
import com.moyeorock.domain.join.enums.JoinStatus;
import com.moyeorock.domain.join.service.JoinRequestService;
import com.moyeorock.global.common.dto.ApiResponse;
import com.moyeorock.global.common.dto.PageResponse;
import com.moyeorock.global.common.enums.TargetType;
import com.moyeorock.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// 경로는 복수형 join-requests다 — Notion `API 초안`이 복수형으로 바뀌어 따랐다(2026-11-03 팀 확인).
// 규칙의 단수 예외는 이제 recruit-post 하나뿐이다(api-conventions.md §5).
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class JoinRequestController {

    private final JoinRequestService joinRequestService;

    @PostMapping("/join-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<JoinRequestResponse> apply(@AuthUser Long userId,
            @Valid @RequestBody JoinRequestCreateRequest request) {
        return ApiResponse.success(joinRequestService.apply(userId, request));
    }

    @GetMapping("/join-requests")
    public ApiResponse<PageResponse<JoinRequestResponse>> getReceived(@AuthUser Long userId,
            @RequestParam(required = false) TargetType targetType, @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) JoinStatus status, @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(joinRequestService.getReceived(userId, targetType, targetId, status, pageable));
    }

    @GetMapping("/users/me/join-requests")
    public ApiResponse<PageResponse<JoinRequestResponse>> getMine(@AuthUser Long userId,
            @RequestParam(required = false) JoinStatus status, @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(joinRequestService.getMine(userId, status, pageable));
    }

    @PatchMapping("/join-requests/{joinRequestId}/status")
    public ApiResponse<JoinResultResponse> updateStatus(@AuthUser Long userId, @PathVariable Long joinRequestId,
            @Valid @RequestBody JoinRequestStatusUpdateRequest request) {
        return ApiResponse.success(joinRequestService.updateStatus(userId, joinRequestId, request.status()));
    }
}

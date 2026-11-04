package com.moyeorock.domain.join.controller;

import com.moyeorock.domain.join.dto.request.InvitationCreateRequest;
import com.moyeorock.domain.join.dto.request.InvitationStatusUpdateRequest;
import com.moyeorock.domain.join.dto.response.InvitationResponse;
import com.moyeorock.domain.join.dto.response.JoinResultResponse;
import com.moyeorock.domain.join.service.JoinRequestService;
import com.moyeorock.global.common.dto.ApiResponse;
import com.moyeorock.global.common.dto.PageResponse;
import com.moyeorock.global.common.enums.JoinStatus;
import com.moyeorock.global.common.enums.TargetType;
import com.moyeorock.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
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

// 엔티티·Service는 가입 신청과 같다(JoinRequestService) — 컨트롤러만 나눈다(domains.md).
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class InvitationController {

    private final JoinRequestService joinRequestService;

    @Operation(summary = "초대 보내기")
    @PostMapping("/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InvitationResponse> invite(@AuthUser Long userId,
            @Valid @RequestBody InvitationCreateRequest request) {
        return ApiResponse.success(joinRequestService.invite(userId, request));
    }

    @Operation(summary = "보낸 초대 목록")
    @GetMapping("/invitations")
    public ApiResponse<PageResponse<InvitationResponse>> getSent(@AuthUser Long userId,
            @RequestParam(required = false) TargetType targetType, @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) JoinStatus status, @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(joinRequestService.getSent(userId, targetType, targetId, status, pageable));
    }

    @Operation(summary = "받은 초대 목록")
    @GetMapping("/users/me/invitations")
    public ApiResponse<PageResponse<InvitationResponse>> getReceived(@AuthUser Long userId,
            @RequestParam(required = false) TargetType targetType, @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) JoinStatus status, @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(
                joinRequestService.getReceivedInvitations(userId, targetType, targetId, status, pageable));
    }

    @Operation(summary = "초대 수락·거절·취소")
    @PatchMapping("/invitations/{invitationId}/status")
    public ApiResponse<JoinResultResponse> updateStatus(@AuthUser Long userId, @PathVariable Long invitationId,
            @Valid @RequestBody InvitationStatusUpdateRequest request) {
        return ApiResponse.success(joinRequestService.updateInvitationStatus(userId, invitationId, request.status()));
    }
}

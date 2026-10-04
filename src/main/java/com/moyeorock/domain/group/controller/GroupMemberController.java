package com.moyeorock.domain.group.controller;

import com.moyeorock.domain.group.dto.request.GroupMemberUpdateRequest;
import com.moyeorock.domain.group.dto.response.GroupMemberResponse;
import com.moyeorock.domain.group.service.GroupMemberService;
import com.moyeorock.global.common.dto.ApiResponse;
import com.moyeorock.global.common.dto.PageResponse;
import com.moyeorock.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/groups/{groupId}/members")
@RequiredArgsConstructor
public class GroupMemberController {

    private final GroupMemberService groupMemberService;

    // 명세의 keyword(닉네임 검색)는 user 도메인 Service가 머지된 뒤에 붙인다 — 지금 받으면 무시만 하게 된다
    @GetMapping
    public ApiResponse<PageResponse<GroupMemberResponse>> getMembers(@AuthUser Long userId,
            @PathVariable Long groupId, @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(groupMemberService.getMembers(userId, groupId, pageable));
    }

    @PatchMapping("/{targetUserId}")
    public ApiResponse<GroupMemberResponse> changeRole(@AuthUser Long userId, @PathVariable Long groupId,
            @PathVariable Long targetUserId, @Valid @RequestBody GroupMemberUpdateRequest request) {
        return ApiResponse.success(groupMemberService.changeRole(userId, groupId, targetUserId, request.role()));
    }

    /** 내보내기·탈퇴. 요청 바디가 없고 서버가 상태를 정한다(본인 → LEFT, 모임장이 타인 → BANNED). */
    @PatchMapping("/{targetUserId}/status")
    public ApiResponse<Void> changeStatus(@AuthUser Long userId, @PathVariable Long groupId,
            @PathVariable Long targetUserId) {
        groupMemberService.changeStatus(userId, groupId, targetUserId);
        return ApiResponse.success(null);
    }
}

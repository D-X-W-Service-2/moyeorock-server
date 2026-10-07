package com.moyeorock.domain.group.controller;

import com.moyeorock.domain.group.dto.request.GroupCreateRequest;
import com.moyeorock.domain.group.dto.request.GroupUpdateRequest;
import com.moyeorock.domain.group.dto.response.GroupDetailResponse;
import com.moyeorock.domain.group.service.GroupService;
import com.moyeorock.global.common.dto.ApiResponse;
import com.moyeorock.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GroupDetailResponse> create(@AuthUser Long userId,
            @Valid @RequestBody GroupCreateRequest request) {
        return ApiResponse.success(groupService.create(userId, request));
    }

    @GetMapping("/{groupId}")
    public ApiResponse<GroupDetailResponse> getDetail(@AuthUser Long userId, @PathVariable Long groupId) {
        return ApiResponse.success(groupService.getDetail(userId, groupId));
    }

    @PutMapping("/{groupId}")
    public ApiResponse<GroupDetailResponse> update(@AuthUser Long userId, @PathVariable Long groupId,
            @Valid @RequestBody GroupUpdateRequest request) {
        return ApiResponse.success(groupService.update(userId, groupId, request));
    }
}

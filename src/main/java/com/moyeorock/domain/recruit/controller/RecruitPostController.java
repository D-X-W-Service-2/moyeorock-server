package com.moyeorock.domain.recruit.controller;

import com.moyeorock.domain.recruit.dto.request.RecruitPostCreateRequest;
import com.moyeorock.domain.recruit.dto.request.RecruitPostSearchCondition;
import com.moyeorock.domain.recruit.dto.request.RecruitPostStatusUpdateRequest;
import com.moyeorock.domain.recruit.dto.request.RecruitPostUpdateRequest;
import com.moyeorock.domain.recruit.dto.response.RecruitPostDetailResponse;
import com.moyeorock.domain.recruit.dto.response.RecruitPostStatusResponse;
import com.moyeorock.domain.recruit.dto.response.RecruitPostSummaryResponse;
import com.moyeorock.domain.recruit.service.RecruitPostService;
import com.moyeorock.global.common.dto.ApiResponse;
import com.moyeorock.global.common.dto.PageResponse;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// 경로가 단수형 recruit-post다 — api-spec.md §4 "경로 표기": `API 초안`을 신뢰해 반영한
// api-conventions.md §5 "리소스는 복수형" 규칙의 명시적 예외(join-request와 함께 2개뿐).
@RestController
@RequestMapping("/v1/recruit-post")
@RequiredArgsConstructor
public class RecruitPostController {

    private final RecruitPostService recruitPostService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RecruitPostDetailResponse> create(@AuthUser Long userId,
            @Valid @RequestBody RecruitPostCreateRequest request) {
        return ApiResponse.success(recruitPostService.create(userId, request));
    }

    @GetMapping
    public ApiResponse<PageResponse<RecruitPostSummaryResponse>> search(RecruitPostSearchCondition condition,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(recruitPostService.search(condition, pageable));
    }

    @GetMapping("/{postId}")
    public ApiResponse<RecruitPostDetailResponse> getDetail(@AuthUser Long userId, @PathVariable Long postId) {
        return ApiResponse.success(recruitPostService.getDetail(userId, postId));
    }

    @PutMapping("/{postId}")
    public ApiResponse<RecruitPostDetailResponse> update(@AuthUser Long userId, @PathVariable Long postId,
            @Valid @RequestBody RecruitPostUpdateRequest request) {
        return ApiResponse.success(recruitPostService.update(userId, postId, request));
    }

    @PatchMapping("/{postId}/status")
    public ApiResponse<RecruitPostStatusResponse> updateStatus(@AuthUser Long userId, @PathVariable Long postId,
            @Valid @RequestBody RecruitPostStatusUpdateRequest request) {
        return ApiResponse.success(recruitPostService.close(userId, postId, request));
    }
}

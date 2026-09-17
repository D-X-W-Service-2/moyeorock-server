package com.moyeorock.domain.recruit.controller;

import com.moyeorock.domain.recruit.dto.request.RecruitPostCreateRequest;
import com.moyeorock.domain.recruit.dto.request.RecruitPostStatusUpdateRequest;
import com.moyeorock.domain.recruit.dto.request.RecruitPostUpdateRequest;
import com.moyeorock.domain.recruit.dto.response.RecruitPostDetailResponse;
import com.moyeorock.domain.recruit.dto.response.RecruitPostStatusResponse;
import com.moyeorock.domain.recruit.dto.response.RecruitPostSummaryResponse;
import com.moyeorock.domain.recruit.enums.RecruitStatus;
import com.moyeorock.domain.recruit.service.RecruitPostService;
import com.moyeorock.global.common.dto.ApiResponse;
import com.moyeorock.global.common.dto.PageResponse;
import com.moyeorock.global.common.enums.Region;
import com.moyeorock.global.common.enums.TargetType;
import com.moyeorock.global.security.AuthUser;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// authorId=me 별칭(api-conventions.md §5)은 아직 처리하지 않는다. Long 파라미터에 "me" 문자열이
// 오면 타입 불일치로 400이 난다 — me 별칭은 여러 도메인이 똑같이 필요로 하는 공통 관심사라
// RecruitPostController 안에서만 임시로 풀지 않고, global 레벨 처리 방식이 정해지면 그때 반영한다.
// 경로가 단수형 recruit-post다 — api-spec.md §4 "경로 표기": `API 초안`을 신뢰해 반영한
// api-conventions.md §5 "리소스는 복수형" 규칙의 명시적 예외(join-request와 함께 2개뿐).
@RestController
@RequestMapping("/v1/recruit-post")
@RequiredArgsConstructor
public class RecruitPostController {

    private final RecruitPostService recruitPostService;

    @PostMapping
    public ResponseEntity<ApiResponse<RecruitPostDetailResponse>> create(
            @AuthUser Long userId, @Valid @RequestBody RecruitPostCreateRequest request) {
        RecruitPostDetailResponse response = recruitPostService.create(userId, request);
        return ResponseEntity.created(URI.create("/v1/recruit-post/" + response.id()))
                .body(ApiResponse.success(response));
    }

    @GetMapping
    public ApiResponse<PageResponse<RecruitPostSummaryResponse>> list(
            @RequestParam(required = false) TargetType targetType,
            @RequestParam(required = false) Region region,
            @RequestParam(required = false) RecruitStatus status,
            @RequestParam(required = false) Long authorId,
            Pageable pageable) {
        return ApiResponse.success(
                recruitPostService.search(targetType, region, status, authorId, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<RecruitPostDetailResponse> getDetail(@AuthUser Long userId, @PathVariable Long id) {
        return ApiResponse.success(recruitPostService.getDetail(userId, id));
    }

    @PutMapping("/{id}")
    public ApiResponse<RecruitPostDetailResponse> update(@AuthUser Long userId, @PathVariable Long id,
            @Valid @RequestBody RecruitPostUpdateRequest request) {
        return ApiResponse.success(recruitPostService.update(userId, id, request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<RecruitPostStatusResponse> updateStatus(@AuthUser Long userId, @PathVariable Long id,
            @Valid @RequestBody RecruitPostStatusUpdateRequest request) {
        return ApiResponse.success(recruitPostService.close(userId, id, request));
    }
}

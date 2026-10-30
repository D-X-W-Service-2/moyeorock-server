package com.moyeorock.domain.performance.controller;

import com.moyeorock.domain.performance.dto.request.PerformanceCreateRequest;
import com.moyeorock.domain.performance.dto.request.PerformanceUpdateRequest;
import com.moyeorock.domain.performance.dto.response.PerformanceDetailResponse;
import com.moyeorock.domain.performance.dto.response.PerformanceSummaryResponse;
import com.moyeorock.domain.performance.service.PerformanceService;
import com.moyeorock.global.common.dto.ApiResponse;
import com.moyeorock.global.common.dto.PageResponse;
import com.moyeorock.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공연은 생성·목록만 모임 하위 경로({@code /v1/groups/{groupId}/performances})를 쓰고,
 * 단건 조회·수정은 단독 경로({@code /v1/performances/{performanceId}})를 쓴다(api-conventions.md §5).
 * 경로가 둘이라 클래스 레벨 {@code @RequestMapping}을 두지 않는다.
 */
@RestController
@RequiredArgsConstructor
public class PerformanceController {

    private final PerformanceService performanceService;

    @Operation(summary = "공연 생성")
    @PostMapping("/v1/groups/{groupId}/performances")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PerformanceDetailResponse> create(@AuthUser Long userId, @PathVariable Long groupId,
            @Valid @RequestBody PerformanceCreateRequest request) {
        return ApiResponse.success(performanceService.create(userId, groupId, request));
    }

    /** 정렬은 공연일 늦은 순 고정이라 {@code sort}를 보내도 쓰지 않는다. */
    @Operation(summary = "모임 공연 목록")
    @GetMapping("/v1/groups/{groupId}/performances")
    public ApiResponse<PageResponse<PerformanceSummaryResponse>> getList(@AuthUser Long userId,
            @PathVariable Long groupId, @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(performanceService.getList(userId, groupId, pageable));
    }

    @Operation(summary = "공연 상세")
    @GetMapping("/v1/performances/{performanceId}")
    public ApiResponse<PerformanceDetailResponse> getDetail(@AuthUser Long userId,
            @PathVariable Long performanceId) {
        return ApiResponse.success(performanceService.getDetail(userId, performanceId));
    }

    @Operation(summary = "공연 정보 수정")
    @PutMapping("/v1/performances/{performanceId}")
    public ApiResponse<PerformanceDetailResponse> update(@AuthUser Long userId, @PathVariable Long performanceId,
            @Valid @RequestBody PerformanceUpdateRequest request) {
        return ApiResponse.success(performanceService.update(userId, performanceId, request));
    }
}

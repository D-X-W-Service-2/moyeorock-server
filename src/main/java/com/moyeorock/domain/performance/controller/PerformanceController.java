package com.moyeorock.domain.performance.controller;

import com.moyeorock.domain.performance.dto.request.PerformanceCreateRequest;
import com.moyeorock.domain.performance.dto.request.PerformanceStatusUpdateRequest;
import com.moyeorock.domain.performance.dto.request.PerformanceTeamCreateRequest;
import com.moyeorock.domain.performance.dto.request.PerformanceUpdateRequest;
import com.moyeorock.domain.performance.dto.request.SetlistConfirmRequest;
import com.moyeorock.domain.performance.dto.request.SetlistSubmitRequest;
import com.moyeorock.domain.performance.dto.response.PerformanceDetailResponse;
import com.moyeorock.domain.performance.dto.response.PerformanceStatusResponse;
import com.moyeorock.domain.performance.dto.response.PerformanceSummaryResponse;
import com.moyeorock.domain.performance.dto.response.SetlistResponse;
import com.moyeorock.domain.performance.enums.PerformancePeriod;
import com.moyeorock.domain.performance.service.PerformanceService;
import com.moyeorock.domain.team.dto.response.TeamDetailResponse;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공연은 생성·목록만 모임 하위 경로({@code /v1/groups/{groupId}/performances})를 쓰고,
 * 단건 조회·수정·상태 변경·팀 생성·셋리스트는 단독 경로({@code /v1/performances/{performanceId}})를 쓴다(api-conventions.md §5).
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

    /**
     * {@code period}(UPCOMING·PAST)는 선택이고 없으면 전체다. 정렬은 period마다 고정이라 {@code sort}를 보내도 쓰지 않는다.
     * 없는 period 값은 전역 처리대로 400이다.
     */
    @Operation(summary = "모임 공연 목록")
    @GetMapping("/v1/groups/{groupId}/performances")
    public ApiResponse<PageResponse<PerformanceSummaryResponse>> getList(@AuthUser Long userId,
            @PathVariable Long groupId, @RequestParam(required = false) PerformancePeriod period,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(performanceService.getList(userId, groupId, period, pageable));
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

    /** 종료({@code DONE})·취소({@code CANCELED})로 바꾸면 소속 팀이 모두 해체되고, 끝난 공연은 다시 바꿀 수 없다. */
    @Operation(summary = "공연 상태 변경")
    @PatchMapping("/v1/performances/{performanceId}/status")
    public ApiResponse<PerformanceStatusResponse> changeStatus(@AuthUser Long userId,
            @PathVariable Long performanceId, @Valid @RequestBody PerformanceStatusUpdateRequest request) {
        return ApiResponse.success(performanceService.changeStatus(userId, performanceId, request));
    }

    /** 응답은 팀 상세(TeamDetailResponse)다 — 만든 사람이 팀장이고, 끝난 공연에는 만들 수 없다. */
    @Operation(summary = "공연 내 팀 생성")
    @PostMapping("/v1/performances/{performanceId}/teams")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TeamDetailResponse> createTeam(@AuthUser Long userId, @PathVariable Long performanceId,
            @Valid @RequestBody PerformanceTeamCreateRequest request) {
        return ApiResponse.success(performanceService.createTeam(userId, performanceId, request));
    }

    /** 확정 전에는 팀들이 넘긴 곡 전체, 확정 후에는 확정곡만 순서대로 준다. 팀 페이지는 teamId로 걸러 확정곡을 보여준다. */
    @Operation(summary = "공연 셋리스트 조회")
    @GetMapping("/v1/performances/{performanceId}/setlist")
    public ApiResponse<SetlistResponse> getSetlist(@AuthUser Long userId, @PathVariable Long performanceId) {
        return ApiResponse.success(performanceService.getSetlist(userId, performanceId));
    }

    /** 그 팀이 넘긴 곡 전체를 바꾼다(팀 선택곡 안에서만). 확정된 뒤에는 409, 모임장이 확정을 해제하면 다시 넘길 수 있다. */
    @Operation(summary = "셋리스트로 넘기기")
    @PutMapping("/v1/performances/{performanceId}/setlist/teams/{teamId}")
    public ApiResponse<SetlistResponse> submitSetlist(@AuthUser Long userId, @PathVariable Long performanceId,
            @PathVariable Long teamId, @Valid @RequestBody SetlistSubmitRequest request) {
        return ApiResponse.success(performanceService.submitSetlist(userId, performanceId, teamId, request));
    }

    /** 팀들이 넘긴 곡 중 요청한 곡만 순서대로 남긴다(나머지는 빠짐). 순서는 1부터 빈틈없이, 끝난 공연은 409. */
    @Operation(summary = "공연 셋리스트 확정")
    @PutMapping("/v1/performances/{performanceId}/setlist")
    public ApiResponse<SetlistResponse> confirmSetlist(@AuthUser Long userId, @PathVariable Long performanceId,
            @Valid @RequestBody SetlistConfirmRequest request) {
        return ApiResponse.success(performanceService.confirmSetlist(userId, performanceId, request));
    }

    /** 확정 시각과 순서를 비워 팀이 다시 넘길 수 있게 한다. 확정 전이거나 끝난 공연이면 409. */
    @Operation(summary = "공연 셋리스트 확정 해제")
    @PatchMapping("/v1/performances/{performanceId}/setlist/unconfirm")
    public ApiResponse<SetlistResponse> unconfirmSetlist(@AuthUser Long userId, @PathVariable Long performanceId) {
        return ApiResponse.success(performanceService.unconfirmSetlist(userId, performanceId));
    }
}

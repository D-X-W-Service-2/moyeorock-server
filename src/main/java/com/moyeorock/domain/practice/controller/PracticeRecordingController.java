package com.moyeorock.domain.practice.controller;

import com.moyeorock.domain.practice.dto.request.PracticeRecordingCreateRequest;
import com.moyeorock.domain.practice.dto.response.PracticeRecordingDetailResponse;
import com.moyeorock.domain.practice.dto.response.PracticeRecordingSummaryResponse;
import com.moyeorock.domain.practice.service.PracticeRecordingService;
import com.moyeorock.global.common.dto.ApiResponse;
import com.moyeorock.global.common.dto.PageResponse;
import com.moyeorock.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
// /v1/users/me/practice-recordings는 /v1/practice-recordings 아래가 아니라서 공통 경로를 /v1로 둔다(소유 도메인은 practice)
@RequestMapping("/v1")
@RequiredArgsConstructor
public class PracticeRecordingController {

    private final PracticeRecordingService practiceRecordingService;

    // 분석은 비동기라 접수만 하고 202를 돌려준다 — 결과는 상세를 다시 조회해서 본다.
    @Operation(summary = "연습 녹음 분석 요청")
    @PostMapping("/practice-recordings")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<PracticeRecordingSummaryResponse> create(@AuthUser Long userId,
            @Valid @RequestBody PracticeRecordingCreateRequest request) {
        return ApiResponse.success(practiceRecordingService.create(userId, request));
    }

    @Operation(summary = "내 연습 녹음 목록")
    @GetMapping("/users/me/practice-recordings")
    public ApiResponse<PageResponse<PracticeRecordingSummaryResponse>> getMyRecordings(@AuthUser Long userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(practiceRecordingService.getMyRecordings(userId, pageable));
    }

    @Operation(summary = "연습 녹음 상세 (분석 결과)")
    @GetMapping("/practice-recordings/{recordingId}")
    public ApiResponse<PracticeRecordingDetailResponse> getDetail(@AuthUser Long userId,
            @PathVariable Long recordingId) {
        return ApiResponse.success(practiceRecordingService.getDetail(userId, recordingId));
    }

    // 상태만 바꾸는 게 아니라 분석 작업을 새로 접수해서 노션대로 POST·202다(api-conventions.md §5의 예외).
    @Operation(summary = "연습 녹음 분석 다시 요청")
    @PostMapping("/practice-recordings/{recordingId}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<PracticeRecordingSummaryResponse> retry(@AuthUser Long userId,
            @PathVariable Long recordingId) {
        return ApiResponse.success(practiceRecordingService.retry(userId, recordingId));
    }

    @Operation(summary = "연습 녹음 삭제")
    @DeleteMapping("/practice-recordings/{recordingId}")
    public ApiResponse<Void> delete(@AuthUser Long userId, @PathVariable Long recordingId) {
        practiceRecordingService.delete(userId, recordingId);
        return ApiResponse.success();
    }
}

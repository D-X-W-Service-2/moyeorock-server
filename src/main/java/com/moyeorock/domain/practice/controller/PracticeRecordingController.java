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
    @Operation(summary = "연습 녹음 분석 요청", description = """
            연습한 곡(`songId`, 곡 검색 API의 id)과 녹음 파일(`audioUrl`, 파일 업로드 API `PRACTICE_AUDIO`의 fileUrl)로 분석을 요청한다.
            접수만 하고 202를 준다 — 상세를 폴링해 `DONE`·`FAILED`를 본다.

            - 녹음은 **3~60초**, 파일은 **1.95MB 이하**(mp3·m4a·wav)여야 한다. 원곡은 AI 서버가 갖고 있다.
            - AI가 분석을 지원하는 곡은 시범 곡뿐이다. 지원하지 않는 곡·원곡과 맞추지 못한 녹음·길이가 맞지 않는 녹음은 `FAILED`가 되고
              `failureReason`에 사유가 온다.

            **에러**
            - 400 `VALIDATION_FAILED`: 입력 오류, 내가 올린 연습 녹음 파일이 아님, 업로드가 끝나지 않음, 1.95MB 초과
            - 404 `SONG_NOT_FOUND`: 없는 곡
            - 409 `PRACTICE_ANALYSIS_IN_PROGRESS`: 진행 중인 분석이 이미 있음
            """)
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

package com.moyeorock.domain.song.controller;

import com.moyeorock.domain.song.dto.request.SongsUpsertRequest;
import com.moyeorock.domain.song.dto.response.SongResponse;
import com.moyeorock.domain.song.dto.response.SongsUpsertResponse;
import com.moyeorock.domain.song.service.SongService;
import com.moyeorock.global.common.dto.ApiResponse;
import com.moyeorock.global.common.dto.PageResponse;
import com.moyeorock.global.common.enums.Genre;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 곡 검색(화면용, JWT)과 AI 서버의 곡 일괄 등록·갱신(서버 간, API 키). 경로가 둘이라 클래스 레벨
 * {@code @RequestMapping}을 두지 않는다(PerformanceController와 같은 방식).
 */
@RestController
@RequiredArgsConstructor
public class SongController {

    private final SongService songService;

    /** keyword는 제목·아티스트 부분 일치, genre는 선택. 정렬은 제목 순 고정이라 {@code sort}를 보내도 쓰지 않는다. */
    @Operation(summary = "곡 검색")
    @GetMapping("/v1/songs")
    public ApiResponse<PageResponse<SongResponse>> search(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) Genre genre, @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(songService.search(keyword, genre, pageable));
    }

    /**
     * AI 서버 전용. 사용자 JWT가 아니라 {@code X-Internal-Api-Key} 헤더로 인증한다(InternalApiKeyFilter).
     * externalId 기준으로 없으면 만들고 있으면 바꾸며, 요청에 없는 곡은 지우지 않는다.
     */
    @Operation(summary = "곡 일괄 등록·갱신 (AI 서버 전용)")
    @PutMapping("/v1/internal/songs")
    public ApiResponse<SongsUpsertResponse> upsertAll(@Valid @RequestBody SongsUpsertRequest request) {
        return ApiResponse.success(songService.upsertAll(request));
    }
}

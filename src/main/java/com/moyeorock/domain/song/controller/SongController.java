package com.moyeorock.domain.song.controller;

import com.moyeorock.domain.song.dto.response.SongResponse;
import com.moyeorock.domain.song.dto.response.SongSyncResponse;
import com.moyeorock.domain.song.service.SongService;
import com.moyeorock.global.common.dto.ApiResponse;
import com.moyeorock.global.common.dto.PageResponse;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/songs")
@RequiredArgsConstructor
public class SongController {

    private final SongService songService;

    /** keyword는 제목·아티스트 부분 일치, genre는 선택. 정렬은 제목 순 고정이라 {@code sort}를 보내도 쓰지 않는다. */
    @Operation(summary = "곡 검색")
    @GetMapping
    public ApiResponse<PageResponse<SongResponse>> search(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) Genre genre, @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(songService.search(keyword, genre, pageable));
    }

    @Operation(summary = "곡 수동 동기화 (AI 카탈로그)", description = """
            매일 04:00에 도는 AI 곡 카탈로그 동기화를 지금 바로 돌린다. dev에서 곡을 채우거나 AI 서버 연동을 확인할 때 쓴다.
            화면에서 부르는 API가 아니다.

            **호출 조건**
            - 서버 환경변수 `AI_SONG_SYNC_ADMIN_USER_IDS`(쉼표로 구분)에 적은 사용자만 부를 수 있다. 비어 있으면 아무도 못 부른다.
            - `AI_SERVER_BASE_URL`이 있어야 한다. 스케줄 켜기(`AI_SONG_SYNC_ENABLED`)와는 무관하다.

            **동작**
            - 카탈로그를 처음부터 끝까지 받을 때까지 응답을 기다린다(페이지마다 AI 응답을 최대 30초 기다린다).
            - 저장 규칙은 스케줄과 같다 — `externalId` 기준으로 없으면 만들고 있으면 바꾸며, 카탈로그에서 빠진 곡은 지우지 않는다.
            - 검증에 걸린 곡은 건너뛰고 `skipped`로 센다. 이유는 서버 로그에 남는다.
            - AI 서버 호출 형식은 곡 검색 API 설명과 `api-spec.md` §12를 본다.

            **에러**
            - 401 `UNAUTHORIZED`: 인증 필요
            - 403 `NO_PERMISSION`: 지정한 사용자가 아님
            - 409 `SONG_SYNC_IN_PROGRESS`: 동기화가 이미 돌고 있음(스케줄 포함)
            - 503 `SONG_SYNC_UNAVAILABLE`: AI 서버 주소 미설정·호출 실패·시간 초과·응답 형식 위반. 실패 전에 저장한 페이지는 남는다
            """)
    @PostMapping("/sync")
    public ApiResponse<SongSyncResponse> sync(@AuthUser Long userId) {
        return ApiResponse.success(songService.syncFromAiByAdmin(userId));
    }
}

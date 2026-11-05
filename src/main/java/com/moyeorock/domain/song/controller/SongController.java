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

    // AI 팀이 Swagger만 보고 쓰는 내부 API라 summary 외에 인증·호출 규칙을 적는다(api-conventions.md §9 예외, 이슈 #147).
    // 허용 enum 값(장르·파트·수준)은 Swagger가 DTO 스키마에서 자동으로 보여 주므로 여기 다시 적지 않는다 — enum이 바뀌면 낡는다.
    private static final String UPSERT_DESCRIPTION = """
            AI 서버가 자기 카탈로그의 곡을 모여락 곡 DB(`songs`)에 넣고 갱신하는 **서버 간 전용 API**입니다. 프론트에서는 부르지 않습니다.

            ### 인증
            - 헤더 `X-Internal-Api-Key: {전달받은 키}`를 붙입니다. 로그인 토큰(`Authorization`)은 필요 없고, 넣어도 쓰지 않습니다.
            - Swagger에서 시험할 때는 오른쪽 위 **Authorize** → `internalApiKey` 칸에 키를 넣습니다.
            - 키가 없거나 틀리면 `401 UNAUTHORIZED`입니다.

            ### 동작
            - `externalId`(AI 쪽 곡 id)로 같은 곡을 찾습니다. 처음 보는 곡은 **새로 만들고**, 이미 있는 곡은 **보낸 값으로 통째로 바꿉니다**
              (`songKey`·`bpm`·`difficulty`를 빼고 보내면 비워집니다). 곡 `id`는 바뀌지 않습니다.
            - 같은 요청을 다시 보내도 결과가 같습니다 — 전체 목록을 처음부터 다시 보내도 안전합니다.
            - 요청에 없는 곡은 **지우지 않습니다**.
            - 한 요청은 전부 저장되거나 전부 실패합니다. 하나라도 틀리면 아무것도 저장되지 않습니다.

            ### 보내는 방법
            - 한 번에 **1~500곡**입니다. 더 많으면 500곡씩 나눠 **하나씩 차례로** 보내 주세요(동시에 보내면 같은 새 곡이 겹칠 때 한쪽이 실패할 수 있습니다).
            - 한 요청 안에 같은 `externalId`가 두 번 있으면 400입니다.

            ### 필드
            | 필드 | 필수 | 규칙 |
            |---|---|---|
            | `externalId` | O | AI 쪽 곡 id, 100자 이하 |
            | `title` | O | 200자 이하 |
            | `artist` | O | 100자 이하 |
            | `genre` | O | 아래 Schema의 enum 값 중 하나. 맞는 장르가 없으면 `ETC` |
            | `songKey` | X | 10자 이하 (예: `Bb`) |
            | `bpm` | X | 양수 |
            | `difficulty` | X | `[{"part", "level"}]` 배열. `part`·`level`은 Schema의 enum 값. 한 곡에 같은 `part`는 한 번만. 없거나 빈 배열이면 난이도 정보 없음 |

            ### 요청 예시
            ```json
            {
              "songs": [
                {
                  "externalId": "yt:fJ9rUzIMcZQ",
                  "title": "Bohemian Rhapsody",
                  "artist": "Queen",
                  "genre": "ROCK",
                  "songKey": "Bb",
                  "bpm": 72,
                  "difficulty": [
                    { "part": "VOCAL", "level": "ADVANCED" },
                    { "part": "DRUM", "level": "INTERMEDIATE" }
                  ]
                }
              ]
            }
            ```

            ### 응답
            `created`는 새로 만든 곡 수, `updated`는 이미 있어서 바꾼 곡 수입니다(내용이 같아도 `updated`로 셉니다). 둘을 더하면 보낸 곡 수입니다.

            ### 에러
            - `400 VALIDATION_FAILED` — `message`에 몇 번째 곡의 어떤 필드인지 담깁니다. 예: `songs[3].externalId: 같은 요청 안에 같은 외부 곡 아이디가 있습니다.`
              없는 enum 값(장르 등)이나 JSON 형식 오류는 위치 없이 `입력값이 올바르지 않습니다.`입니다.
            - `401 UNAUTHORIZED` — `X-Internal-Api-Key`가 없거나 틀림
            """;

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
    @Operation(summary = "곡 일괄 등록·갱신 (AI 서버 전용)", description = UPSERT_DESCRIPTION)
    @PutMapping("/v1/internal/songs")
    public ApiResponse<SongsUpsertResponse> upsertAll(@Valid @RequestBody SongsUpsertRequest request) {
        return ApiResponse.success(songService.upsertAll(request));
    }
}

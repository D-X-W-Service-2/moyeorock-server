package com.moyeorock.domain.song.controller;

import com.moyeorock.domain.song.dto.response.SongResponse;
import com.moyeorock.domain.song.service.SongService;
import com.moyeorock.global.common.dto.ApiResponse;
import com.moyeorock.global.common.dto.PageResponse;
import com.moyeorock.global.common.enums.Genre;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/songs")
@RequiredArgsConstructor
public class SongController {

    private final SongService songService;

    /** keyword는 제목·아티스트 부분 일치, genre는 선택. 정렬은 제목 순 고정이라 {@code sort}를 보내도 쓰지 않는다. */
    @Operation(summary = "곡 검색", description = """
            곡 마스터를 검색한다.

            **검색 조건**
            - `keyword`(선택): 제목·아티스트 부분 일치. 앞뒤 공백은 지우고, 비어 있으면 조건 없이 전체다.
            - `genre`(선택): 장르 하나로 거른다.
            - 정렬은 제목 순 고정이다. `sort`를 보내도 쓰지 않는다.
            - `difficulty`는 정보가 있는 파트만 담고, 난이도 정보가 없으면 빈 배열이다.

            **곡 데이터는 어디서 오나**
            - 곡은 화면에서 넣지 않는다. 백엔드가 AI 서버 곡 카탈로그를 받아와 채운다(AI 서버는 백엔드를 부르지 않는다).
            - 매일 04:00(Asia/Seoul)에 돈다. `AI_SONG_SYNC_ENABLED=true`이고 `AI_SERVER_BASE_URL`이 있을 때만 돈다.
            - `externalId`(AI 쪽 곡 id, 대소문자 구분) 기준으로 없으면 만들고 있으면 받은 값으로 통째로 바꾼다. 곡 `id`는 그대로다.
            - 카탈로그에서 빠진 곡도 지우지 않는다(팀 선택곡·셋리스트가 참조한다).

            **백엔드 → AI 서버 호출 형식** (AI 팀 참고 · 잠정, 바뀌면 이 설명과 `api-spec.md` §12를 함께 고친다)

            `GET {AI_SERVER_BASE_URL}/v1/songs?limit=500&cursor={nextCursor}`, 헤더 `Authorization: Bearer {AI_SERVER_API_KEY}`(키를 설정했을 때만)
            - 첫 요청에는 `cursor`가 없다. 응답의 `nextCursor`가 `null`이 될 때까지 그 값을 그대로 넣어 다음 페이지를 부른다.
            - 페이지마다 따로 저장한다. 중간에 실패해도 앞 페이지는 남고, 다음 회차에 처음부터 다시 받는다.
            - 같은 `nextCursor`를 다시 돌려주면 그 회차를 끝낸다.

            응답 — 값은 우리 enum 그대로다(`genre`는 `Genre`, `part`는 `Instrument`, `level`은 `Level`).
            ```json
            {
              "songs": [
                {
                  "externalId": "youtube:Lb3mQdm-UHs",
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
              ],
              "nextCursor": "eyJpZCI6NTAwfQ"
            }
            ```
            - 필수: `songs`, `externalId`(100자 이하), `title`(200자 이하), `artist`(100자 이하), `genre`
            - 선택: `songKey`(10자 이하), `bpm`(양수), `difficulty`(없거나 빈 배열이면 난이도 정보 없음)
            - 필수 값 누락·길이 초과·`externalId` 앞뒤 공백·한 곡의 같은 파트 중복·한 페이지 안 `externalId` 중복은 그 곡만 건너뛰고 나머지는 저장한다.
            - `songs`가 없거나 우리 enum에 없는 값이 오면 그 회차가 실패한다(이미 저장한 앞 페이지는 남는다).
            """)
    @GetMapping
    public ApiResponse<PageResponse<SongResponse>> search(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) Genre genre, @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(songService.search(keyword, genre, pageable));
    }
}

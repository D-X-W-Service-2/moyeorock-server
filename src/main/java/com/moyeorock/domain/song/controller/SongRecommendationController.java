package com.moyeorock.domain.song.controller;

import com.moyeorock.domain.song.dto.response.TeamSongRecommendationResponse;
import com.moyeorock.domain.song.service.SongRecommendationService;
import com.moyeorock.global.common.dto.ApiResponse;
import com.moyeorock.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 곡 추천(AI, 이슈 #171). 팀 선택곡 저장·조회(SetlistController)와 경로 앞부분이 같지만 song 도메인이 받는다. */
@RestController
@RequestMapping("/v1/teams/{teamId}/songs/recommendations")
@RequiredArgsConstructor
public class SongRecommendationController {

    private final SongRecommendationService songRecommendationService;

    @Operation(summary = "팀 곡 추천 (AI)", description = """
            팀 장르와 팀원 세션별 실력을 바탕으로 AI 서버가 고른 곡을 추천받는다. 팀 선택곡 후보를 고를 때 쓴다.

            **호출 조건**
            - 이 팀의 활동 중인 팀원이면 누구나 호출할 수 있다(독립 팀·공연 팀 모두).
            - 요청 본문과 쿼리 파라미터는 없다.

            **동작**
            - AI에는 팀 장르와 팀원마다 이 팀에서 맡은 세션·그 악기의 실력을 보낸다. 실력을 등록하지 않은 팀원은 빠진다.
            - AI가 고른 곡 중 우리 DB에 아직 없는 곡(동기화 전)은 빼고, 점수 높은 순 최대 10곡을 돌려준다. 고른 곡이 없으면 `songs`는 빈 배열이고 200이다.
            - `score`는 0~1 정렬용 점수이며 선호 확률이 아니다.
            - `reasons`는 백엔드가 만든 추천 근거다. 팀 장르와 곡 장르가 같으면 `팀 장르 일치: 록`, 팀이 가진 세션마다 곡 난이도와 비교해 `보컬 실력 충족` 또는 `드럼 난이도 초과`. 곡에 그 세션 난이도 정보가 없으면 그 세션 문구는 없다.
            - `selectedByOtherTeam`은 같은 공연의 다른 팀이 이미 선택곡으로 고른 곡이면 `true`다. 독립 팀이면 항상 `false`다.
            - AI 서버 응답을 최대 30초까지 기다린다.

            **에러**
            - 401 `UNAUTHORIZED`: 인증 필요
            - 403 `NO_PERMISSION`: 이 팀의 활동 중인 팀원이 아님
            - 404 `TEAM_NOT_FOUND`: 없는 팀이거나 해체된 팀
            - 503 `SONG_RECOMMENDATION_UNAVAILABLE`: AI 서버 호출 실패·시간 초과·주소 미설정

            **백엔드 → AI 서버 호출 형식** (AI 팀 참고, 바뀌면 이 설명과 `api-spec.md` §12를 함께 고친다)

            `POST {AI_RECOMMEND_URL}/v1/recommendations/songs`, 헤더 `X-AI-Recommend-Key: {AI_RECOMMEND_API_KEY}`, `Content-Type: application/json`
            (전용 값이 없으면 `AI_SERVER_BASE_URL`·`AI_SERVER_API_KEY`를 쓴다)

            요청: 후보 곡은 보내지 않는다.
            ```json
            { "mode": "TEAM", "genres": ["ROCK"], "members": [ { "part": "BASS", "level": "NOVICE" } ], "limit": 20 }
            ```

            응답: `songs` 배열은 필수다(고른 곡이 없으면 빈 배열). `externalId`나 `score`가 없는 항목은 버린다.
            ```json
            { "songs": [ { "externalId": "local-abc", "score": 0.875 } ], "ranker": "content_rule" }
            ```
            """)
    @GetMapping
    public ApiResponse<TeamSongRecommendationResponse> recommendForTeam(@AuthUser Long userId,
            @PathVariable Long teamId) {
        return ApiResponse.success(songRecommendationService.recommendForTeam(userId, teamId));
    }
}

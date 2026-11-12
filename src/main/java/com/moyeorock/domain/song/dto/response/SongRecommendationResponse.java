package com.moyeorock.domain.song.dto.response;

import java.util.List;

/**
 * 내 취향 추천곡(GET /v1/users/me/songs/recommendations, 이슈 #172). {@code songs}는 추천 순서(점수 높은 순)이고 최대 2곡이다.
 * 온보딩 전 사용자이거나 AI가 고른 곡이 우리 DB에 없으면 빈 배열이다. 팀 곡 추천과 달리 추천 근거는 없다(dto-naming §10).
 */
public record SongRecommendationResponse(
        List<RecommendedSong> songs
) {

    /** 추천곡 하나. {@code song}은 곡 검색과 같은 전체 정보다. {@code score}는 0~1 정렬용 점수이며 선호 확률이 아니다. */
    public record RecommendedSong(
            SongResponse song,
            double score
    ) {
    }
}

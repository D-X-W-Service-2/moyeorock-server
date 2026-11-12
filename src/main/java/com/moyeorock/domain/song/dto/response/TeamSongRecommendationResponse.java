package com.moyeorock.domain.song.dto.response;

import java.util.List;

/**
 * 팀 곡 추천(GET /v1/teams/{teamId}/songs/recommendations, 이슈 #171). {@code songs}는 추천 순서(점수 높은 순)이고
 * 최대 10곡이다. AI가 고른 곡 중 우리 DB에 없는 곡은 빠진다.
 */
public record TeamSongRecommendationResponse(
        Long teamId,
        List<RecommendedSong> songs
) {

    /**
     * 추천곡 하나. {@code song}은 곡 검색과 같은 전체 정보다. {@code score}는 0~1 정렬용 점수이며 선호 확률이 아니다.
     * {@code reasons}는 백엔드가 만든 추천 근거(AI는 주지 않는다). {@code selectedByOtherTeam}은 같은 공연의 다른 팀이 이미
     * 선택곡으로 고른 곡인지이고, 독립 팀이면 항상 {@code false}다.
     */
    public record RecommendedSong(
            SongResponse song,
            double score,
            List<String> reasons,
            boolean selectedByOtherTeam
    ) {
    }
}

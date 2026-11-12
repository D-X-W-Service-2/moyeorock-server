package com.moyeorock.domain.song.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record SongRecommendationResponse(
        @Schema(description = "후속 선택·저장 기록에 보내는 추천 요청 ID") String requestId,
        @Schema(description = "추천 순서로 정렬된 곡, 최대 20개. 카탈로그가 비면 빈 배열")
        List<RecommendedSong> songs) {

    public record RecommendedSong(
            SongResponse song,
            @Schema(description = "0~1 정렬용 점수. 선호 확률이 아님", minimum = "0", maximum = "1", example = "0.875")
            double score) {
    }
}

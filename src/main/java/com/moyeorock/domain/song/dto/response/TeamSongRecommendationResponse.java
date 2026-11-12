package com.moyeorock.domain.song.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record TeamSongRecommendationResponse(
        @Schema(description = "추천 대상 팀 ID", example = "5") Long teamId,
        @Schema(description = "후속 선택·저장 기록에 보내는 추천 요청 ID") String requestId,
        @Schema(description = "추천 순서로 정렬된 곡, 최대 20개. 카탈로그가 비면 빈 배열")
        List<RecommendedTeamSong> songs) {

    public record RecommendedTeamSong(
            SongResponse song,
            @Schema(description = "0~1 정렬용 점수. 선호 확률이 아님", minimum = "0", maximum = "1", example = "0.825")
            double score,
            @Schema(description = "추천 근거. 장르 일치·파트 난이도 정보·다른 팀 선택 여부")
            List<String> reasons,
            @Schema(description = "같은 공연의 다른 팀이 이미 선택한 곡인지. 독립 팀은 항상 false")
            boolean selectedByOtherTeam) {
    }
}

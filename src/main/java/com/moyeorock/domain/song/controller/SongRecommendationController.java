package com.moyeorock.domain.song.controller;

import com.moyeorock.domain.song.dto.response.SongRecommendationResponse;
import com.moyeorock.domain.song.dto.response.TeamSongRecommendationResponse;
import com.moyeorock.domain.song.service.SongRecommendationService;
import com.moyeorock.global.common.dto.ApiResponse;
import com.moyeorock.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class SongRecommendationController {

    private final SongRecommendationService recommendationService;

    @Operation(summary = "내 취향 추천곡",
            description = "요청 본문과 쿼리 파라미터가 없습니다. JWT의 사용자 ID와 프로필 장르·세션 실력으로 최대 20곡을 반환합니다. score는 정렬 점수이며 선호 확률이 아닙니다.")
    @GetMapping("/users/me/songs/recommendations")
    public ApiResponse<SongRecommendationResponse> recommend(@AuthUser Long userId) {
        return ApiResponse.success(recommendationService.recommend(userId));
    }

    @Operation(summary = "팀 곡 추천",
            description = "요청 본문과 쿼리 파라미터가 없습니다. 활동 중인 팀원만 호출할 수 있습니다. 팀 장르·담당 악기 실력으로 최대 20곡을 반환하며, 같은 공연의 다른 팀이 이미 선택한 곡을 표시합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK",
                    useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "NO_PERMISSION: 활동 중인 팀원이 아님",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "TEAM_NOT_FOUND: 팀이 없거나 해체됨",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    })
    @GetMapping("/teams/{teamId}/songs/recommendations")
    public ApiResponse<TeamSongRecommendationResponse> recommendTeam(@AuthUser Long userId,
            @Parameter(description = "추천 대상 팀 ID", example = "5") @PathVariable Long teamId) {
        return ApiResponse.success(recommendationService.recommendTeam(userId, teamId));
    }
}

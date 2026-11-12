package com.moyeorock.domain.song.controller;

import com.moyeorock.domain.song.service.RecommendationEventService;
import com.moyeorock.domain.song.service.RecommendationEventService.SongFeedbackAction;
import com.moyeorock.global.common.dto.ApiResponse;
import com.moyeorock.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Follow-up action for a song that this user was actually served in a recommendation. */
@RestController
@RequestMapping("/v1/recommendations")
@RequiredArgsConstructor
public class RecommendationFeedbackController {

    private final RecommendationEventService eventService;

    public record SongFeedbackRequest(@NotNull @Positive Long songId,
                                      @NotNull SongFeedbackAction action) {
    }

    @Operation(summary = "추천곡 후속 행동 기록",
            description = "추천 응답의 requestId와 그 응답에 있던 songId를 보내세요. "
                    + "실제 화면에 보인 곡은 VIEWED, 이후 CLICKED·BOOKMARKED·SELECTED를 전송합니다. "
                    + "같은 행동의 재전송은 한 번만 기록합니다.")
    @PostMapping("/{requestId}/feedback")
    public ApiResponse<Void> feedback(@AuthUser Long userId, @PathVariable String requestId,
                                      @Valid @RequestBody SongFeedbackRequest request) {
        eventService.recordFeedback(userId, requestId, request.songId(), request.action());
        return ApiResponse.success();
    }
}

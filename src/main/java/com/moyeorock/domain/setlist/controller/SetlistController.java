package com.moyeorock.domain.setlist.controller;

import com.moyeorock.domain.setlist.dto.request.TeamSongsSaveRequest;
import com.moyeorock.domain.setlist.dto.response.TeamSongsResponse;
import com.moyeorock.domain.setlist.service.SetlistService;
import com.moyeorock.global.common.dto.ApiResponse;
import com.moyeorock.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 팀 선택곡. 팀 쪽에서는 "셋리스트"라는 말을 쓰지 않는다 — 공연 셋리스트 엔드포인트는 PerformanceController에 있다. */
@RestController
@RequestMapping("/v1/teams/{teamId}/songs")
@RequiredArgsConstructor
public class SetlistController {

    private final SetlistService setlistService;

    /** 기존 선택곡 전체를 요청 목록으로 바꾼다(빈 목록이면 전부 삭제). */
    @Operation(summary = "팀 선택곡 저장")
    @PutMapping
    public ApiResponse<TeamSongsResponse> saveTeamSongs(@AuthUser Long userId, @PathVariable Long teamId,
            @Valid @RequestBody TeamSongsSaveRequest request) {
        return ApiResponse.success(setlistService.saveTeamSongs(userId, teamId, request));
    }

    @Operation(summary = "팀 선택곡 조회")
    @GetMapping
    public ApiResponse<TeamSongsResponse> getTeamSongs(@AuthUser Long userId, @PathVariable Long teamId) {
        return ApiResponse.success(setlistService.getTeamSongs(userId, teamId));
    }
}

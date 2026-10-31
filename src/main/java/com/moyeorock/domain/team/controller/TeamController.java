package com.moyeorock.domain.team.controller;

import com.moyeorock.domain.team.dto.request.TeamCreateRequest;
import com.moyeorock.domain.team.dto.request.TeamStatusUpdateRequest;
import com.moyeorock.domain.team.dto.request.TeamUpdateRequest;
import com.moyeorock.domain.team.dto.response.TeamDetailResponse;
import com.moyeorock.domain.team.dto.response.TeamStatusResponse;
import com.moyeorock.domain.team.dto.response.TeamSummaryResponse;
import com.moyeorock.domain.team.service.TeamService;
import com.moyeorock.global.common.dto.ApiResponse;
import com.moyeorock.global.common.dto.PageResponse;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Region;
import com.moyeorock.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
// /v1/users/me/teams는 /v1/teams 아래가 아니라서 공통 경로를 /v1로 둔다(소유 도메인은 team)
@RequestMapping("/v1")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    @PostMapping("/teams")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TeamDetailResponse> create(@AuthUser Long userId,
            @Valid @RequestBody TeamCreateRequest request) {
        return ApiResponse.success(teamService.create(userId, request));
    }

    // status 파라미터는 받지 않는다 — 해체된 팀은 항상 목록에서 빠진다(결정 8)
    @GetMapping("/teams")
    public ApiResponse<PageResponse<TeamSummaryResponse>> search(
            @RequestParam(required = false) Region region,
            @RequestParam(required = false) Genre genre,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(teamService.search(region, genre, pageable));
    }

    @GetMapping("/teams/{teamId}")
    public ApiResponse<TeamDetailResponse> getDetail(@AuthUser Long userId, @PathVariable Long teamId) {
        return ApiResponse.success(teamService.getDetail(userId, teamId));
    }

    @GetMapping("/users/me/teams")
    public ApiResponse<PageResponse<TeamSummaryResponse>> getMyTeams(@AuthUser Long userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(teamService.getMyTeams(userId, pageable));
    }

    @PutMapping("/teams/{teamId}")
    public ApiResponse<Void> update(@AuthUser Long userId, @PathVariable Long teamId,
            @Valid @RequestBody TeamUpdateRequest request) {
        teamService.update(userId, teamId, request);
        return ApiResponse.success();
    }

    @PatchMapping("/teams/{teamId}/status")
    public ApiResponse<TeamStatusResponse> disband(@AuthUser Long userId, @PathVariable Long teamId,
            @Valid @RequestBody TeamStatusUpdateRequest request) {
        return ApiResponse.success(teamService.disband(userId, teamId, request));
    }
}

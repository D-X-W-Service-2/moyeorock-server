package com.moyeorock.domain.team.controller;

import com.moyeorock.domain.team.dto.request.TeamMemberStatusUpdateRequest;
import com.moyeorock.domain.team.dto.request.TeamMemberUpdateRequest;
import com.moyeorock.domain.team.dto.response.TeamMemberResponse;
import com.moyeorock.domain.team.dto.response.TeamMemberStatusResponse;
import com.moyeorock.domain.team.dto.response.TeamMembersResponse;
import com.moyeorock.domain.team.service.TeamService;
import com.moyeorock.global.common.dto.ApiResponse;
import com.moyeorock.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/teams/{teamId}/members")
@RequiredArgsConstructor
public class TeamMemberController {

    private final TeamService teamService;

    @GetMapping
    public ApiResponse<TeamMembersResponse> getMembers(@PathVariable Long teamId) {
        return ApiResponse.success(teamService.getMembers(teamId));
    }

    @PatchMapping("/{userId}")
    public ApiResponse<TeamMemberResponse> changeMember(@AuthUser Long requesterId, @PathVariable Long teamId,
            @PathVariable Long userId, @Valid @RequestBody TeamMemberUpdateRequest request) {
        return ApiResponse.success(teamService.changeMember(requesterId, teamId, userId, request));
    }

    @PatchMapping("/{userId}/status")
    public ApiResponse<TeamMemberStatusResponse> changeMemberStatus(@AuthUser Long requesterId,
            @PathVariable Long teamId, @PathVariable Long userId,
            @Valid @RequestBody TeamMemberStatusUpdateRequest request) {
        return ApiResponse.success(teamService.changeMemberStatus(requesterId, teamId, userId, request));
    }
}

package com.moyeorock.domain.team.controller;

import com.moyeorock.domain.team.dto.request.TeamMemberStatusUpdateRequest;
import com.moyeorock.domain.team.dto.request.TeamMemberUpdateRequest;
import com.moyeorock.domain.team.dto.response.TeamMemberRecommendationResponse;
import com.moyeorock.domain.team.dto.response.TeamMemberResponse;
import com.moyeorock.domain.team.dto.response.TeamMemberStatusResponse;
import com.moyeorock.domain.team.dto.response.TeamMembersResponse;
import com.moyeorock.domain.team.service.TeamService;
import com.moyeorock.global.common.dto.ApiResponse;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/teams/{teamId}/members")
@RequiredArgsConstructor
public class TeamMemberController {

    private final TeamService teamService;

    @Operation(summary = "팀원 목록")
    @GetMapping
    public ApiResponse<TeamMembersResponse> getMembers(@PathVariable Long teamId) {
        return ApiResponse.success(teamService.getMembers(teamId));
    }

    @Operation(summary = "팀원 추천 (AI)", description = """
            팀에 필요한 세션(악기)에 맞는 팀원 후보를 AI 서버로 추천받는다.

            **호출 조건**
            - 팀장만 호출할 수 있다. 해체된 팀은 호출할 수 없다.
            - `instrument`(필수)는 찾는 세션(악기)이다.

            **동작**
            - 후보는 활동 중이고 추천을 허용한(`is_recommendable`) 사용자 중 `instrument` 세션을 가진 사람이다. 이미 이 팀의 팀원인 사람은 뺀다.
            - 조건에 맞는 사용자가 200명을 넘으면 무작위 200명만 평가하므로, 호출할 때마다 결과가 조금씩 달라질 수 있다.
            - 응답은 점수 높은 순 최대 10명이다. 후보가 없으면 `candidates`는 빈 배열이고 200이다.
            - AI 서버 응답을 최대 30초까지 기다린다.

            **에러**
            - 400 `VALIDATION_FAILED`: `instrument`가 없거나 없는 값
            - 401 `UNAUTHORIZED`: 인증 필요
            - 403 `NOT_TEAM_LEADER`: 팀장이 아님
            - 404 `TEAM_NOT_FOUND`: 없는 팀
            - 409 `INVALID_STATE`: 해체된 팀
            - 503 `RECOMMENDATION_UNAVAILABLE`: AI 서버 호출 실패·시간 초과·주소 미설정

            **백엔드 → AI 서버 호출 형식** (AI 팀 참고 · 잠정, 바뀌면 이 설명과 `api-spec.md` §12를 함께 고친다)

            `POST {AI_SERVER_BASE_URL}/v1/recommendations/members`, 헤더 `Authorization: Bearer {AI_SERVER_API_KEY}`, `Content-Type: application/json`

            요청 — 값은 ERD 컬럼과 우리 enum 그대로다.
            ```json
            {
              "team": { "id": 5, "region": "SEOUL", "genres": ["ROCK", "POP"] },
              "instrument": "DRUM",
              "members": [ { "userId": 3, "instrument": "VOCAL", "level": "ADVANCED" } ],
              "candidates": [ { "userId": 30, "region": "SEOUL", "genres": ["ROCK"], "level": "INTERMEDIATE" } ]
            }
            ```
            - `team.region`, `candidates[].region`·`genres`는 온보딩 전이면 `null`일 수 있다.
            - `members[].instrument`는 그 사람이 이 팀에서 맡은 세션, `members[].level`은 그 악기에서의 실력(없으면 `null`)이다.
            - `candidates[].level`은 요청한 `instrument`에서의 실력이다.

            응답 — `candidates` 배열은 필수다(후보를 못 골랐으면 빈 배열). 우리가 보내지 않은 `userId`와 `score`가 없는 항목은 버린다.
            ```json
            { "candidates": [ { "userId": 30, "score": 0.87, "reasons": ["장르 일치: ROCK", "활동 지역 동일"] } ] }
            ```
            """)
    @GetMapping("/recommendations")
    public ApiResponse<TeamMemberRecommendationResponse> recommendMembers(@AuthUser Long requesterId,
            @PathVariable Long teamId, @RequestParam Instrument instrument) {
        return ApiResponse.success(teamService.recommendMembers(requesterId, teamId, instrument));
    }

    @Operation(summary = "팀원 역할·세션 변경")
    @PatchMapping("/{userId}")
    public ApiResponse<TeamMemberResponse> changeMember(@AuthUser Long requesterId, @PathVariable Long teamId,
            @PathVariable Long userId, @Valid @RequestBody TeamMemberUpdateRequest request) {
        return ApiResponse.success(teamService.changeMember(requesterId, teamId, userId, request));
    }

    @Operation(summary = "팀원 탈퇴·내보내기")
    @PatchMapping("/{userId}/status")
    public ApiResponse<TeamMemberStatusResponse> changeMemberStatus(@AuthUser Long requesterId,
            @PathVariable Long teamId, @PathVariable Long userId,
            @Valid @RequestBody TeamMemberStatusUpdateRequest request) {
        return ApiResponse.success(teamService.changeMemberStatus(requesterId, teamId, userId, request));
    }
}

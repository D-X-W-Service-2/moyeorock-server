package com.moyeorock.domain.rehearsal.controller;

import com.moyeorock.domain.rehearsal.dto.request.RehearsalCreateRequest;
import com.moyeorock.domain.rehearsal.dto.request.RehearsalUpdateRequest;
import com.moyeorock.domain.rehearsal.dto.response.RehearsalDetailResponse;
import com.moyeorock.domain.rehearsal.dto.response.RehearsalsResponse;
import com.moyeorock.domain.rehearsal.service.RehearsalService;
import com.moyeorock.global.common.dto.ApiResponse;
import com.moyeorock.global.common.dto.DeleteResponse;
import com.moyeorock.global.security.AuthUser;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
// /v1/teams/{teamId}/rehearsals, /v1/rehearsals, /v1/users/me/rehearsals 세 갈래라서 공통 경로를 /v1로 둔다
@RequestMapping("/v1")
@RequiredArgsConstructor
public class RehearsalController {

    private final RehearsalService rehearsalService;

    // from·to는 2026-09-01 같은 날짜다. 생략 규칙(이번 달)과 기간 상한은 Service가 정한다.
    @GetMapping("/teams/{teamId}/rehearsals")
    public ApiResponse<RehearsalsResponse> getTeamRehearsals(@AuthUser Long userId, @PathVariable Long teamId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(rehearsalService.getTeamRehearsals(userId, teamId, from, to));
    }

    @PostMapping("/teams/{teamId}/rehearsals")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RehearsalDetailResponse> create(@AuthUser Long userId, @PathVariable Long teamId,
            @Valid @RequestBody RehearsalCreateRequest request) {
        return ApiResponse.success(rehearsalService.create(userId, teamId, request));
    }

    @GetMapping("/rehearsals/{id}")
    public ApiResponse<RehearsalDetailResponse> getDetail(@AuthUser Long userId, @PathVariable Long id) {
        return ApiResponse.success(rehearsalService.getDetail(userId, id));
    }

    @PutMapping("/rehearsals/{id}")
    public ApiResponse<RehearsalDetailResponse> update(@AuthUser Long userId, @PathVariable Long id,
            @Valid @RequestBody RehearsalUpdateRequest request) {
        return ApiResponse.success(rehearsalService.update(userId, id, request));
    }

    // 삭제 응답은 204가 아니라 200 + DeleteResponse다(api-conventions.md)
    @DeleteMapping("/rehearsals/{id}")
    public ApiResponse<DeleteResponse> delete(@AuthUser Long userId, @PathVariable Long id) {
        return ApiResponse.success(rehearsalService.delete(userId, id));
    }

    @GetMapping("/users/me/rehearsals")
    public ApiResponse<RehearsalsResponse> getMyRehearsals(@AuthUser Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(rehearsalService.getMyRehearsals(userId, from, to));
    }
}

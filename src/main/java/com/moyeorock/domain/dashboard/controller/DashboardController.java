package com.moyeorock.domain.dashboard.controller;

import com.moyeorock.domain.dashboard.dto.response.DashboardResponse;
import com.moyeorock.domain.dashboard.service.DashboardService;
import com.moyeorock.global.common.dto.ApiResponse;
import com.moyeorock.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "대시보드 통합 조회")
    @GetMapping
    public ApiResponse<DashboardResponse> getDashboard(@AuthUser Long userId) {
        return ApiResponse.success(dashboardService.getDashboard(userId));
    }
}

package com.moyeorock.domain.user.controller;

import com.moyeorock.domain.user.dto.request.OnboardingCreateRequest;
import com.moyeorock.domain.user.dto.request.UserInstrumentUpdateRequest;
import com.moyeorock.domain.user.dto.request.UserUpdateRequest;
import com.moyeorock.domain.user.dto.response.NicknameCheckResponse;
import com.moyeorock.domain.user.dto.response.UserInstrumentsResponse;
import com.moyeorock.domain.user.dto.response.UserMeResponse;
import com.moyeorock.domain.user.dto.response.UserSummaryResponse;
import com.moyeorock.domain.user.dto.response.UserWithdrawResponse;
import com.moyeorock.domain.user.service.UserService;
import com.moyeorock.global.common.dto.ApiResponse;
import com.moyeorock.global.common.dto.PageResponse;
import com.moyeorock.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * user Phase 1 7개(api-spec.md §1). 전부 로그인 필수. 토큰 없으면 필터에서 401.
 * Service 호출과 {@code ApiResponse} 감싸기만 한다. Phase 2(/me/activities, /{id})는 이슈 #90.
 * 온보딩 등록은 재호출이 멱등이라(값만 갱신) 201이 아니라 200이다.
 */
@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "내 프로필 조회")
    @GetMapping("/me")
    public ApiResponse<UserMeResponse> getMe(@AuthUser Long userId) {
        return ApiResponse.success(userService.getMe(userId));
    }

    @Operation(summary = "프로필 설정 / 수정")
    @PutMapping("/me")
    public ApiResponse<UserMeResponse> updateMe(@AuthUser Long userId, @Valid @RequestBody UserUpdateRequest request) {
        return ApiResponse.success(userService.updateMe(userId, request));
    }

    @Operation(summary = "탈퇴")
    @DeleteMapping("/me")
    public ApiResponse<UserWithdrawResponse> withdraw(@AuthUser Long userId) {
        return ApiResponse.success(userService.withdraw(userId));
    }

    @Operation(summary = "온보딩 등록")
    @PostMapping("/me/onboarding")
    public ApiResponse<UserMeResponse> completeOnboarding(@AuthUser Long userId,
            @Valid @RequestBody OnboardingCreateRequest request) {
        return ApiResponse.success(userService.completeOnboarding(userId, request));
    }

    @Operation(summary = "세션/실력 수정")
    @PutMapping("/me/instruments")
    public ApiResponse<UserInstrumentsResponse> updateInstruments(@AuthUser Long userId,
            @Valid @RequestBody UserInstrumentUpdateRequest request) {
        return ApiResponse.success(userService.updateInstruments(userId, request));
    }

    /** 검색어가 비거나 없으면 Service가 400을 낸다(전체 회원 목록 경로를 열지 않는다). 정렬은 Service가 닉네임 오름차순으로 고정한다. */
    @Operation(summary = "사용자 검색")
    @GetMapping("/search")
    public ApiResponse<PageResponse<UserSummaryResponse>> search(@RequestParam(required = false) String nickname,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(userService.search(nickname, pageable));
    }

    @Operation(summary = "닉네임 중복 확인")
    @GetMapping("/nickname/check")
    public ApiResponse<NicknameCheckResponse> checkNickname(@RequestParam String nickname) {
        return ApiResponse.success(userService.checkNickname(nickname));
    }
}

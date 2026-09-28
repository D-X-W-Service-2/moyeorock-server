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
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "user", description = "사용자")
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

    @Operation(summary = "프로필 설정 / 수정 (전체 교체)")
    @PutMapping("/me")
    public ApiResponse<UserMeResponse> updateMe(@AuthUser Long userId,
                                                @Valid @RequestBody UserUpdateRequest request) {
        return ApiResponse.success(userService.updateMe(userId, request));
    }

    // 소프트 삭제지만 명세가 DELETE라 그대로 둔다 (api-conventions §5 예외, 팀장 확인 중 — 검토 문서 B-4).
    // PATCH /me/status 로 바뀌면 이 매핑만 바꾸면 된다.
    @Operation(summary = "탈퇴 (소프트 삭제)")
    @DeleteMapping("/me")
    public ApiResponse<UserWithdrawResponse> withdraw(@AuthUser Long userId) {
        return ApiResponse.success(userService.withdraw(userId));
    }

    // 201이 아니라 200: 기존 users 행을 채우는 수정이고 재호출도 같은 응답(멱등)이라 생성으로 보지 않는다.
    @Operation(summary = "온보딩 등록 (재호출 시 갱신)")
    @PostMapping("/me/onboarding")
    public ApiResponse<UserMeResponse> completeOnboarding(@AuthUser Long userId,
                                                          @Valid @RequestBody OnboardingCreateRequest request) {
        return ApiResponse.success(userService.completeOnboarding(userId, request));
    }

    @Operation(summary = "세션/실력 수정 (전체 교체)")
    @PutMapping("/me/instruments")
    public ApiResponse<UserInstrumentsResponse> updateInstruments(@AuthUser Long userId,
                                                                  @Valid @RequestBody UserInstrumentUpdateRequest request) {
        return ApiResponse.success(userService.updateInstruments(userId, request));
    }

    @Operation(summary = "닉네임 중복 확인")
    @GetMapping("/nickname/check")
    public ApiResponse<NicknameCheckResponse> checkNickname(@AuthUser Long userId,
                                                            @RequestParam String nickname) {
        return ApiResponse.success(userService.checkNickname(nickname));
    }

    // size 상한 20은 spring.data.web.pageable.max-page-size(전역). 정렬은 Service에서 닉네임 오름차순으로 고정.
    @Operation(summary = "사용자 검색 (닉네임 부분 일치, ACTIVE만)")
    @GetMapping("/search")
    public ApiResponse<PageResponse<UserSummaryResponse>> search(@AuthUser Long userId,
                                                                 @RequestParam String nickname,
                                                                 @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(userService.search(nickname, pageable));
    }
}

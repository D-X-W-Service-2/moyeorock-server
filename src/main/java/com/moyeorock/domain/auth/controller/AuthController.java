package com.moyeorock.domain.auth.controller;

import com.moyeorock.domain.auth.dto.request.KakaoSignupRequest;
import com.moyeorock.domain.auth.dto.request.UserLoginRequest;
import com.moyeorock.domain.auth.dto.request.UserSignupRequest;
import com.moyeorock.domain.auth.dto.response.AuthTokenResponse;
import com.moyeorock.domain.auth.dto.response.KakaoAuthResponse;
import com.moyeorock.domain.auth.service.AuthService;
import com.moyeorock.global.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 인증이 필요 없는 유일한 그룹 — PublicEndpoints AUTH("/v1/auth/**"). */
@Tag(name = "auth", description = "회원가입·로그인")
@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "이메일 회원가입 (가입 직후 로그인 상태)")
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthTokenResponse> signup(@Valid @RequestBody UserSignupRequest request) {
        return ApiResponse.success(authService.signup(request));
    }

    @Operation(summary = "이메일 로그인")
    @PostMapping("/login")
    public ApiResponse<AuthTokenResponse> login(@Valid @RequestBody UserLoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @Operation(summary = "카카오 인증 — 기존 회원이면 로그인, 신규면 가입용 토큰")
    @PostMapping("/kakao")
    public ApiResponse<KakaoAuthResponse> kakaoAuth(@RequestParam String code) {
        return ApiResponse.success(authService.kakaoAuth(code));
    }

    @Operation(summary = "카카오 가입 완료 (약관 동의)")
    @PostMapping("/kakao/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthTokenResponse> kakaoSignup(@Valid @RequestBody KakaoSignupRequest request) {
        return ApiResponse.success(authService.kakaoSignup(request));
    }
}

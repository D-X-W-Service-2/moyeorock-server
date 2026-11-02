package com.moyeorock.domain.auth.controller;

import com.moyeorock.domain.auth.dto.request.KakaoSignupRequest;
import com.moyeorock.domain.auth.dto.request.UserLoginRequest;
import com.moyeorock.domain.auth.dto.request.UserSignupRequest;
import com.moyeorock.domain.auth.dto.response.AuthTokenResponse;
import com.moyeorock.domain.auth.dto.response.KakaoAuthResponse;
import com.moyeorock.domain.auth.service.AuthService;
import com.moyeorock.global.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 가입·로그인 4개(api-spec.md §11). 전부 인증 불필요. {@code PublicEndpoints.AUTH}("/v1/auth/**")로 필터를 통과한다.
 * Service 호출과 {@code ApiResponse} 감싸기만 하고 로직을 두지 않는다(PR #67 선례).
 * 계정이 생성되는 두 요청(signup·kakao/signup)만 201이다.
 */
@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthTokenResponse> signup(@Valid @RequestBody UserSignupRequest request) {
        return ApiResponse.success(authService.signup(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthTokenResponse> login(@Valid @RequestBody UserLoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    /** 계정을 만들지 않는다. 기존 회원이면 auth에 토큰, 신규면 signupToken만 채워 돌려준다. */
    @PostMapping("/kakao")
    public ApiResponse<KakaoAuthResponse> kakaoAuth(@RequestParam String code) {
        return ApiResponse.success(authService.kakaoAuth(code));
    }

    @PostMapping("/kakao/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthTokenResponse> kakaoSignup(@Valid @RequestBody KakaoSignupRequest request) {
        return ApiResponse.success(authService.kakaoSignup(request));
    }
}

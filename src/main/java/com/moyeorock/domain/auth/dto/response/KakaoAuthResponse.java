package com.moyeorock.domain.auth.dto.response;

/**
 * POST /v1/auth/kakao 응답. 기존 회원이면 auth, 신규면 signupToken·kakaoNickname만 채운다 (나머지는 null).
 * 인가 코드가 1회용이라 한 호출에서 두 경우를 모두 처리해야 해서 한 응답 타입에 담는다 (2026-09-28 결정).
 */
public record KakaoAuthResponse(boolean isNewUser, AuthTokenResponse auth, String signupToken, String kakaoNickname) {

    public static KakaoAuthResponse loggedIn(AuthTokenResponse auth) {
        return new KakaoAuthResponse(false, auth, null, null);
    }

    public static KakaoAuthResponse signupRequired(String signupToken, String kakaoNickname) {
        return new KakaoAuthResponse(true, null, signupToken, kakaoNickname);
    }
}

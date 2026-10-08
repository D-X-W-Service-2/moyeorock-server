package com.moyeorock.domain.auth.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** POST /v1/auth/kakao/signup — 카카오 인증 응답의 signupToken + 약관 동의. 이 요청에서 계정이 생성된다. */
public record KakaoSignupRequest(
        @NotBlank String signupToken,
        @NotNull @AssertTrue(message = "개인정보 처리방침에 동의해야 합니다.") Boolean privacyAgreed
) {
}

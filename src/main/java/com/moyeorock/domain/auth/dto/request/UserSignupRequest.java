package com.moyeorock.domain.auth.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * POST /v1/auth/signup. name은 users.nickname 임시값으로 저장되고 온보딩에서 정식 닉네임으로 바뀐다.
 * 비밀번호 규칙(8~64자, 영문·숫자·특수문자 각 1자 이상, 공백 불가)은 가입에서만 검사한다 — 2026-09-28 결정.
 */
public record UserSignupRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank
        @Size(min = 8, max = 64, message = "비밀번호는 8자 이상 64자 이하여야 합니다.")
        @Pattern(regexp = PASSWORD_PATTERN, message = "비밀번호는 영문·숫자·특수문자를 각각 1자 이상 포함하고 공백이 없어야 합니다.")
        String password,
        @NotBlank @Size(max = 20) String name,
        @NotNull @AssertTrue(message = "개인정보 처리방침에 동의해야 합니다.") Boolean privacyAgreed
) {
    /** 영문 1+, 숫자 1+, 영문·숫자 외 문자(특수문자) 1+, 공백 없음. 길이는 @Size가 담당. */
    public static final String PASSWORD_PATTERN = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z\\d\\s])\\S+$";
}

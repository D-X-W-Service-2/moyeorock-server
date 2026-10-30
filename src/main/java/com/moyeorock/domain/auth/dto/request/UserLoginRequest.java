package com.moyeorock.domain.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** POST /v1/auth/login. 비밀번호 규칙은 검사하지 않는다(형식 노출 방지) — 빈 값만 400. */
public record UserLoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {
}

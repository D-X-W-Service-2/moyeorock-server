package com.moyeorock.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import com.moyeorock.global.security.InvalidTokenException;
import com.moyeorock.global.security.JwtProperties;
import com.moyeorock.global.security.JwtProvider;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KakaoSignupTokenProviderTest {

    private static final JwtProperties PROPS = new JwtProperties(
            "test-only-secret-key-must-be-at-least-32-bytes-long", "moyeorock-test", 3_600_000L);

    KakaoSignupTokenProvider provider = new KakaoSignupTokenProvider(PROPS);
    JwtProvider jwtProvider = new JwtProvider(PROPS);

    private static ErrorCode errorCodeOf(Throwable thrown) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        return ((BusinessException) thrown).getErrorCode();
    }

    @Test
    @DisplayName("발급한 토큰을 검증하면 kakaoId·닉네임이 그대로 나온다")
    void issue_then_parse_round_trip() {
        String token = provider.issue("123456789", "서준");

        KakaoSignupTokenProvider.SignupClaims claims = provider.parse(token);

        assertThat(claims.kakaoId()).isEqualTo("123456789");
        assertThat(claims.kakaoNickname()).isEqualTo("서준");
    }

    @Test
    @DisplayName("만료된 토큰은 INVALID_SIGNUP_TOKEN")
    void expired_token_rejected() {
        KakaoSignupTokenProvider shortLived = new KakaoSignupTokenProvider(PROPS, Duration.ofMillis(-1_000));
        String token = shortLived.issue("1", "서준");

        assertThat(errorCodeOf(catchThrowable(() -> provider.parse(token)))).isEqualTo(ErrorCode.INVALID_SIGNUP_TOKEN);
    }

    @Test
    @DisplayName("변조된 토큰·빈 문자열은 INVALID_SIGNUP_TOKEN")
    void tampered_or_blank_rejected() {
        String token = provider.issue("1", "서준");
        String tampered = token.substring(0, token.length() - 3) + "abc";

        assertThat(errorCodeOf(catchThrowable(() -> provider.parse(tampered)))).isEqualTo(ErrorCode.INVALID_SIGNUP_TOKEN);
        assertThat(errorCodeOf(catchThrowable(() -> provider.parse("")))).isEqualTo(ErrorCode.INVALID_SIGNUP_TOKEN);
    }

    @Test
    @DisplayName("같은 키로 서명된 액세스 토큰을 가입 토큰으로 내면 typ이 없어 거부된다")
    void access_token_is_not_a_signup_token() {
        String accessToken = jwtProvider.generateToken(12L);

        assertThat(errorCodeOf(catchThrowable(() -> provider.parse(accessToken)))).isEqualTo(ErrorCode.INVALID_SIGNUP_TOKEN);
    }

    @Test
    @DisplayName("반대로 가입 토큰을 액세스 토큰으로 내면 userId가 없어 JwtProvider가 거부한다")
    void signup_token_is_not_an_access_token() {
        String signupToken = provider.issue("1", "서준");

        assertThatThrownBy(() -> jwtProvider.parseToken(signupToken)).isInstanceOf(InvalidTokenException.class);
    }
}

package com.moyeorock.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtProviderTest {

    private static final String SECRET_KEY = "test-jwt-secret-key-for-unit-test-1234567890123456";

    private final JwtProvider jwtProvider = new JwtProvider(
            new JwtProperties(SECRET_KEY, "moyeorock", 1000L * 60 * 60));

    @Test
    @DisplayName("발급한 토큰을 파싱하면 담았던 userId를 그대로 돌려받는다")
    void generateAndParseToken_returnsSameUserId() {
        String token = jwtProvider.generateToken(1L);

        JwtPayload payload = jwtProvider.parseToken(token);

        assertThat(payload.userId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("만료된 토큰을 파싱하면 예외가 발생한다")
    void parseToken_fails_whenTokenExpired() {
        JwtProvider expiredTokenProvider = new JwtProvider(
                new JwtProperties(SECRET_KEY, "moyeorock", -1000L));
        String expiredToken = expiredTokenProvider.generateToken(1L);

        assertThatThrownBy(() -> jwtProvider.parseToken(expiredToken))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("다른 키로 서명된 토큰을 파싱하면 예외가 발생한다")
    void parseToken_fails_whenSignatureInvalid() {
        JwtProvider otherProvider = new JwtProvider(
                new JwtProperties("other-jwt-secret-key-for-unit-test-1234567890", "moyeorock", 1000L * 60 * 60));
        String tokenFromOtherKey = otherProvider.generateToken(1L);

        assertThatThrownBy(() -> jwtProvider.parseToken(tokenFromOtherKey))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("형식이 잘못된 토큰을 파싱하면 예외가 발생한다")
    void parseToken_fails_whenTokenMalformed() {
        assertThatThrownBy(() -> jwtProvider.parseToken("malformed-token"))
                .isInstanceOf(InvalidTokenException.class);
    }
}

package com.moyeorock.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthFilterTest {

    private static final String SECRET_KEY = "test-jwt-secret-key-for-unit-test-1234567890123456";

    private final JwtProvider jwtProvider = new JwtProvider(
            new JwtProperties(SECRET_KEY, "moyeorock", 1000L * 60 * 60));

    private final JwtAuthFilter jwtAuthFilter = new JwtAuthFilter(jwtProvider);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("유효한 토큰이 오면 인증된(authenticated=true) 컨텍스트를 설정한다")
    void doFilterInternal_setsAuthenticatedContext_whenTokenValid() throws Exception {
        String token = jwtProvider.generateToken(1L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = (req, res) -> {};

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getPrincipal()).isEqualTo(1L);
    }

    @Test
    @DisplayName("유효하지 않은 토큰이 오면 컨텍스트를 비운다")
    void doFilterInternal_clearsContext_whenTokenInvalid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = (req, res) -> {};

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}

package com.moyeorock.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * AI 서버 같은 내부 서버가 부르는 {@code /v1/internal/**}의 인증(이슈 #147). 사용자 JWT 대신 {@code X-Internal-Api-Key}
 * 헤더를 서버 설정 {@code internal.api-key}(환경변수 {@code INTERNAL_API_KEY})와 비교해, 맞으면 내부 서버로 인증한다.
 * 맞지 않으면 아무것도 하지 않고 넘겨서 인가 단계가 401로 막는다(JwtAuthFilter와 같은 흐름).
 *
 * <p>키가 비어 있으면(설정을 빠뜨린 배포) 어떤 요청도 통과시키지 않는다 — 내부 API가 열린 채로 뜨지 않게.
 * {@value #MIN_KEY_BYTES}바이트보다 짧은 키도 같다 — 추측하기 쉬운 키로 곡 마스터 전체를 덮어쓸 수 있게 두지 않는다(PR #148 리뷰).
 * 비교는 {@link MessageDigest#isEqual}로 한다(길이가 같으면 내용과 관계없이 같은 시간이 걸린다).
 *
 * <p>Bean으로 등록하지 않는다 — {@code @Component}로 두면 Spring Boot가 모든 요청에 서블릿 필터로도 건다.
 * SecurityConfig의 내부 체인이 직접 만든다.
 */
@Slf4j
public class InternalApiKeyFilter extends OncePerRequestFilter {

    public static final String PATH_PATTERN = "/v1/internal/**";
    public static final String HEADER = "X-Internal-Api-Key";
    // .env.example은 openssl rand -base64 48(64자)로 만들라고 안내한다
    public static final int MIN_KEY_BYTES = 32;
    private static final String PRINCIPAL = "internal-server";

    private final byte[] apiKey;

    public InternalApiKeyFilter(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("internal.api-key(INTERNAL_API_KEY)가 비어 있어 {} 요청을 모두 거절합니다.", PATH_PATTERN);
            this.apiKey = new byte[0];
            return;
        }
        byte[] key = apiKey.getBytes(StandardCharsets.UTF_8);
        if (key.length < MIN_KEY_BYTES) {
            log.warn("internal.api-key(INTERNAL_API_KEY)가 {}바이트보다 짧아 {} 요청을 모두 거절합니다.",
                    MIN_KEY_BYTES, PATH_PATTERN);
            this.apiKey = new byte[0];
            return;
        }
        this.apiKey = key;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (matches(request.getHeader(HEADER))) {
            SecurityContextHolder.getContext().setAuthentication(
                    new PreAuthenticatedAuthenticationToken(PRINCIPAL, null, List.of()));
        }
        filterChain.doFilter(request, response);
    }

    private boolean matches(String header) {
        if (apiKey.length == 0 || header == null) {
            return false;
        }
        return MessageDigest.isEqual(apiKey, header.getBytes(StandardCharsets.UTF_8));
    }
}

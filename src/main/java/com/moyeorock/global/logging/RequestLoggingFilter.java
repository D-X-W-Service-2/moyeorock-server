package com.moyeorock.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

// requestId 발급 · MDC 정리 · 액세스 로그를 한 필터에서 처리한다.
//
// Spring Security 필터 체인보다 앞에 둔다 — 인증 거부(401/403)와 인증 필터 안의 로그에도
// requestId가 남아야 하기 때문이다. 순서는 Security 기본 order에서 상수로 유도한다.
// (JwtAuthFilter는 SecurityFilterChain 안에서 addFilterBefore로 붙는 필터라 이 필터의 안쪽이다.)
@Slf4j
@Component
@Order(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 10)
public class RequestLoggingFilter extends OncePerRequestFilter {

    static final String REQUEST_ID_HEADER = "X-Request-Id";

    private static final int SERVER_ERROR_STATUS = 500;
    private static final String[] EXCLUDED_PATH_PREFIXES = {"/swagger-ui", "/v3/api-docs"};

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        // 인바운드 X-Request-Id는 신뢰하지 않고 항상 새로 발급한다(로그 위조 방지).
        String requestId = UUID.randomUUID().toString();
        MDC.put(MdcKeys.REQUEST_ID, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        long startNanos = System.nanoTime();
        // 예외가 필터 밖으로 전파되면 아직 response.getStatus()가 200일 수 있어서,
        // 정상 종료를 확인하기 전까지는 실패로 간주한다.
        boolean completed = false;
        try {
            filterChain.doFilter(request, response);
            completed = true;
        } finally {
            logAccess(request, completed ? response.getStatus() : SERVER_ERROR_STATUS, startNanos);
            MDC.clear();
        }
    }

    private void logAccess(HttpServletRequest request, int status, long startNanos) {
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        // getRequestURI()는 쿼리스트링을 포함하지 않는다 — /v1/auth/kakao?code= 같은 값이 남지 않는다.
        if (status >= SERVER_ERROR_STATUS) {
            log.error("{} {} {} ({}ms)", request.getMethod(), request.getRequestURI(), status, elapsedMs);
        } else {
            log.info("{} {} {} ({}ms)", request.getMethod(), request.getRequestURI(), status, elapsedMs);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        for (String prefix : EXCLUDED_PATH_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}

package com.moyeorock.global.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("체인 안에서는 requestId가 MDC에 있고, 응답 헤더 X-Request-Id와 같다")
    void doFilter_putsRequestIdInMdc_andEchoesItInHeader() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> requestIdInChain = new AtomicReference<>();

        filter.doFilter(new MockHttpServletRequest("GET", "/v1/anything"), response,
                (req, res) -> requestIdInChain.set(MDC.get(MdcKeys.REQUEST_ID)));

        assertThat(requestIdInChain.get()).isNotBlank();
        assertThat(response.getHeader("X-Request-Id")).isEqualTo(requestIdInChain.get());
    }

    @Test
    @DisplayName("요청이 끝나면 MDC가 비워진다 (스레드 풀 재사용 시 값 누수 방지)")
    void doFilter_clearsMdc_afterRequest() throws Exception {
        filter.doFilter(new MockHttpServletRequest("GET", "/v1/anything"), new MockHttpServletResponse(),
                (req, res) -> MDC.put(MdcKeys.USER_ID, "1"));

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    @DisplayName("인바운드 X-Request-Id 헤더는 무시하고 항상 새로 발급한다")
    void doFilter_ignoresInboundRequestIdHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/anything");
        request.addHeader("X-Request-Id", "attacker-controlled\nFAKE LOG LINE");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertThat(response.getHeader("X-Request-Id")).doesNotContain("attacker");
    }

    @Test
    @DisplayName("액세스 로그에는 쿼리스트링이 없다 (/v1/auth/kakao?code= 같은 값 보호)")
    void doFilter_logsPathOnly_withoutQueryString() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/auth/kakao");
        request.setQueryString("code=SECRET_AUTH_CODE");

        try (LogCapture capture = new LogCapture()) {
            filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});

            assertThat(capture.events()).singleElement().satisfies(event -> {
                assertThat(event.getFormattedMessage()).contains("POST /v1/auth/kakao 200");
                assertThat(event.getFormattedMessage()).doesNotContain("SECRET_AUTH_CODE");
            });
        }
    }

    @Test
    @DisplayName("5xx 응답은 ERROR, 그 외는 INFO로 기록한다")
    void doFilter_logsErrorLevel_forServerErrors() throws Exception {
        try (LogCapture capture = new LogCapture()) {
            filter.doFilter(new MockHttpServletRequest("GET", "/ok"), new MockHttpServletResponse(),
                    (req, res) -> {});
            filter.doFilter(new MockHttpServletRequest("GET", "/boom"), new MockHttpServletResponse(),
                    (req, res) -> ((MockHttpServletResponse) res).setStatus(503));

            assertThat(capture.events()).extracting(ILoggingEvent::getLevel)
                    .containsExactly(Level.INFO, Level.ERROR);
        }
    }

    @Test
    @DisplayName("체인에서 예외가 전파되면 500으로 기록하고 MDC도 정리한 뒤 예외를 그대로 던진다")
    void doFilter_logsAs500_andClearsMdc_whenChainThrows() {
        try (LogCapture capture = new LogCapture()) {
            assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest("GET", "/boom"),
                    new MockHttpServletResponse(), (req, res) -> {
                        throw new IllegalStateException("boom");
                    })).isInstanceOf(IllegalStateException.class);

            assertThat(capture.events()).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getFormattedMessage()).contains("GET /boom 500");
            });
            assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
        }
    }

    @Test
    @DisplayName("Swagger 경로는 필터 대상에서 제외한다")
    void doFilter_skipsSwaggerPaths() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (LogCapture capture = new LogCapture()) {
            filter.doFilter(new MockHttpServletRequest("GET", "/swagger-ui/index.html"), response,
                    (req, res) -> {});

            assertThat(capture.events()).isEmpty();
            assertThat(response.getHeader("X-Request-Id")).isNull();
        }
    }
}

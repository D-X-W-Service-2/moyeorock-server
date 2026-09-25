package com.moyeorock.global.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.moyeorock.global.security.JwtProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

// 실제 필터 체인 순서를 검증한다: RequestLoggingFilter -> Security(JwtAuthFilter 포함) -> 디스패처.
// 단위 테스트는 필터 하나만 돌려서 이 순서 관계는 증명하지 못한다.
@SpringBootTest
@AutoConfigureMockMvc
class RequestLoggingFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Test
    @DisplayName("인증 실패(401) 요청의 액세스 로그에도 requestId가 있고 userId는 없다")
    void unauthenticatedRequest_logsRequestId_withoutUserId() throws Exception {
        try (LogCapture capture = new LogCapture()) {
            MvcResult result = mockMvc.perform(get("/v1/does-not-exist")).andReturn();

            assertThat(result.getResponse().getStatus()).isEqualTo(401);
            ILoggingEvent accessLog = capture.events().get(0);
            assertThat(accessLog.getFormattedMessage()).contains("GET /v1/does-not-exist 401");
            assertThat(accessLog.getMDCPropertyMap())
                    .containsEntry(MdcKeys.REQUEST_ID, result.getResponse().getHeader("X-Request-Id"))
                    .doesNotContainKey(MdcKeys.USER_ID);
        }
    }

    @Test
    @DisplayName("유효한 토큰으로 인증된 요청의 액세스 로그에는 userId도 있다")
    void authenticatedRequest_logsUserId() throws Exception {
        String token = jwtProvider.generateToken(7L);

        try (LogCapture capture = new LogCapture()) {
            MvcResult result = mockMvc.perform(get("/v1/does-not-exist")
                    .header("Authorization", "Bearer " + token)).andReturn();

            ILoggingEvent accessLog = capture.events().get(0);
            assertThat(accessLog.getMDCPropertyMap())
                    .containsEntry(MdcKeys.USER_ID, "7")
                    .containsEntry(MdcKeys.REQUEST_ID, result.getResponse().getHeader("X-Request-Id"));
        }
    }
}

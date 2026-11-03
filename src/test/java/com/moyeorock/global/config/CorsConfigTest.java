package com.moyeorock.global.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moyeorock.config.TestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/** 기본 허용 origin(application.yml cors.allowed-origins, #123)으로 브라우저 프리플라이트가 통과·거부되는지 본다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class CorsConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:3000",
            "http://localhost:5173",
            "https://moyeorock.kro.kr",
            "https://moyeorak.pages.dev",
            "https://moyeorock-client-test-alpha.vercel.app"
    })
    @DisplayName("허용 origin의 프리플라이트는 200이고 그 origin을 돌려준다")
    void preflight_isAllowed_forConfiguredOrigin(String origin) throws Exception {
        mockMvc.perform(options("/v1/users/me")
                        .header(HttpHeaders.ORIGIN, origin)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    @DisplayName("목록에 없는 origin의 프리플라이트는 403이고 허용 헤더가 없다")
    void preflight_isRejected_forUnknownOrigin() throws Exception {
        mockMvc.perform(options("/v1/users/me")
                        .header(HttpHeaders.ORIGIN, "https://evil.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}

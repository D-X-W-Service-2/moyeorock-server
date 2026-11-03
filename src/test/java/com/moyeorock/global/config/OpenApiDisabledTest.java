package com.moyeorock.global.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moyeorock.config.TestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** 문서를 닫는 경우 — 운영 기본값(SWAGGER_ENABLED 없음)과 같은 상태다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@TestPropertySource(properties = "springdoc.api-docs.enabled=false")
class OpenApiDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("문서를 끄면 /v3/api-docs는 404다")
    void apiDocs_isNotFound_whenDisabled() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
    }
}

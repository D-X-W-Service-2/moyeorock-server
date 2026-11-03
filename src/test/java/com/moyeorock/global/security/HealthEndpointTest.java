package com.moyeorock.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moyeorock.config.TestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 배포 스크립트(deploy-dev.yml)가 재시작 뒤 토큰 없이 부르는 헬스 체크.
 * 막히면 앱이 정상이어도 배포가 매번 롤백되므로 공개 여부와 응답 모양을 고정해 둔다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class HealthEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("토큰 없이 /actuator/health를 부르면 200과 UP만 돌려주고 상세는 숨긴다")
    void health_isPublicAndHidesDetails() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }
}

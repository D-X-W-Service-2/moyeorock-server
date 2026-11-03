package com.moyeorock.global.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moyeorock.global.common.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Spring MVC가 기본으로 4xx로 내리던 예외가 catch-all에 잡혀 500이 되지 않는지 검증한다 (이슈 #20)
@WebMvcTest(controllers = GlobalExceptionHandlerTest.ProbeController.class)
@Import(GlobalExceptionHandlerTest.ProbeController.class) // controllers 지정만으로는 등록되지 않아 명시 등록
@AutoConfigureMockMvc(addFilters = false)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("바디 없는 성공 응답 — {success, code, message, data} 4개 키, data는 키가 남고 값만 null")
    void successWithoutBody_keepsNullDataKey() throws Exception {
        mockMvc.perform(get("/probe/empty"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(4)))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("요청에 성공했습니다."))
                .andExpect(jsonPath("$", hasKey("data")))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    @DisplayName("에러 응답 — {success, code, message, data} 4개 키, error 객체 없이 data는 null")
    void businessException_returnsFlatErrorEnvelope() throws Exception {
        mockMvc.perform(get("/probe/business-error"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$", aMapWithSize(4)))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_STATE"))
                .andExpect(jsonPath("$.message").value("처리할 수 없는 상태입니다."))
                .andExpect(jsonPath("$", hasKey("data")))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    @DisplayName("@Valid 검증 실패 — 400 VALIDATION_FAILED, message는 \"필드: 사유\"")
    void validationFailure_returns400_withFieldInMessage() throws Exception {
        mockMvc.perform(post("/probe/valid").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(startsWith("name: ")));
    }

    @Test
    @DisplayName("없는 경로 — 404 RESOURCE_NOT_FOUND")
    void unknownPath_returns404() throws Exception {
        mockMvc.perform(get("/v1/not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("쿼리 파라미터 타입 불일치 — 400 VALIDATION_FAILED, message에 파라미터명")
    void typeMismatch_returns400() throws Exception {
        mockMvc.perform(get("/probe").param("id", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("id: 올바른 값이 아닙니다."));
    }

    @Test
    @DisplayName("필수 쿼리 파라미터 누락 — 400 VALIDATION_FAILED, message에 파라미터명")
    void missingParameter_returns400() throws Exception {
        mockMvc.perform(get("/probe"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("id: 필수 파라미터입니다."));
    }

    @Test
    @DisplayName("미지원 HTTP 메서드 — 405 METHOD_NOT_ALLOWED, Allow 헤더에 지원 메서드")
    void methodNotSupported_returns405() throws Exception {
        mockMvc.perform(delete("/probe"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("깨진 JSON 바디 — 400 VALIDATION_FAILED")
    void malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON).content("{\"name\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("미지원 Content-Type — 415 UNSUPPORTED_MEDIA_TYPE")
    void mediaTypeNotSupported_returns415() throws Exception {
        mockMvc.perform(post("/probe").contentType(MediaType.TEXT_PLAIN).content("plain text"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    // 테스트 전용 컨트롤러 — GET /probe?id={Long}으로 타입 불일치·누락·405를, JSON 전용 POST /probe로 파싱 실패·415를 유도한다.
    // /probe/empty·/probe/business-error·/probe/valid는 봉투 모양(바디 없는 성공·에러·검증 실패)을 본다
    @RestController
    static class ProbeController {

        @GetMapping("/probe/empty")
        ApiResponse<Void> empty() {
            return ApiResponse.success();
        }

        @GetMapping("/probe/business-error")
        ApiResponse<Void> businessError() {
            throw new BusinessException(ErrorCode.INVALID_STATE);
        }

        @PostMapping(value = "/probe/valid", consumes = MediaType.APPLICATION_JSON_VALUE)
        ApiResponse<String> createValid(@Valid @RequestBody ValidatedProbeRequest request) {
            return ApiResponse.success(request.name());
        }

        @GetMapping("/probe")
        ApiResponse<Long> probe(@RequestParam Long id) {
            return ApiResponse.success(id);
        }

        @PostMapping(value = "/probe", consumes = MediaType.APPLICATION_JSON_VALUE)
        ApiResponse<String> create(@RequestBody ProbeRequest request) {
            return ApiResponse.success(request.name());
        }
    }

    record ProbeRequest(String name) {}

    record ValidatedProbeRequest(@NotBlank String name) {}
}

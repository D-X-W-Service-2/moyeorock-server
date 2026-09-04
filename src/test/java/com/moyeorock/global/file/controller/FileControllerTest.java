package com.moyeorock.global.file.controller;

import com.moyeorock.global.file.dto.request.PresignedUrlCreateRequest;
import com.moyeorock.global.file.dto.response.PresignedUrlResponse;
import com.moyeorock.global.file.service.FileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FileController.class)
@AutoConfigureMockMvc(addFilters = false)
class FileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FileService fileService;

    @Test
    @DisplayName("POST /v0/files/presigned-url — 정상 요청은 200과 URL·만료 시각을 반환한다")
    void createPresignedUrl() throws Exception {
        given(fileService.issuePresignedUrl(any(PresignedUrlCreateRequest.class)))
                .willReturn(new PresignedUrlResponse(
                        "https://upload.example.com",
                        "https://file.example.com",
                        LocalDateTime.of(2026, 8, 30, 12, 0)));

        mockMvc.perform(post("/v0/files/presigned-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "domain": "PERFORMANCE_POSTER",
                                  "fileName": "poster.png",
                                  "contentType": "image/png"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.uploadUrl").value("https://upload.example.com"))
                .andExpect(jsonPath("$.data.fileUrl").value("https://file.example.com"))
                .andExpect(jsonPath("$.data.expiresAt").exists());
    }

    @Test
    @DisplayName("fileName이 비면 400")
    void blankFileName() throws Exception {
        mockMvc.perform(post("/v0/files/presigned-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "domain": "PERFORMANCE_POSTER",
                                  "fileName": "",
                                  "contentType": "image/png"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("허용 목록에 없는 contentType이면 400")
    void disallowedContentType() throws Exception {
        mockMvc.perform(post("/v0/files/presigned-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "domain": "PROFILE_IMAGE",
                                  "fileName": "page.html",
                                  "contentType": "text/html"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("정의되지 않은 domain 값이면 400")
    void unknownDomain() throws Exception {
        mockMvc.perform(post("/v0/files/presigned-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "domain": "BANNER",
                                  "fileName": "banner.png",
                                  "contentType": "image/png"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}

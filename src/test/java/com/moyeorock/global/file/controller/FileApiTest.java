package com.moyeorock.global.file.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.global.file.repository.UploadedFileRepository;
import com.moyeorock.global.security.JwtProvider;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 파일 삭제를 fileUrl로 하는지(이슈 #164) 실제 HTTP 흐름으로 확인한다. 업로드 URL 발급 응답의 fileUrl을 그대로 인코딩해
 * 쿼리로 보내면 지워져야 한다 — 프론트가 저장해 둔 URL만으로 지울 수 있어야 한다는 것이 이번 변경의 요점이다.
 * 테스트마다 롤백되므로 저장소 객체 삭제(커밋 뒤)는 FileServiceDeleteTest가 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class FileApiTest {

    private static final String BASE = "/v1/files";
    private static final long UPLOADER = 9_100_001L;
    private static final long OTHER_USER = 9_100_002L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private UploadedFileRepository uploadedFileRepository;

    @Test
    @DisplayName("업로드 URL 발급 때 받은 fileUrl을 인코딩해 보내면 지워지고, 응답 id는 발급 응답의 fileId다")
    void delete_byIssuedFileUrl() throws Exception {
        MvcResult issued = issue(UPLOADER).andExpect(status().isOk()).andReturn();
        String body = issued.getResponse().getContentAsString();
        long fileId = ((Number) JsonPath.read(body, "$.data.fileId")).longValue();
        String fileUrl = JsonPath.read(body, "$.data.fileUrl");

        deleteByUrl(UPLOADER, fileUrl)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(fileId))
                .andExpect(jsonPath("$.data.deleted").value(true));

        assertThat(uploadedFileRepository.findById(fileId)).isEmpty();
    }

    @Test
    @DisplayName("남이 올린 파일의 fileUrl이면 403 NO_PERMISSION이고 지워지지 않는다")
    void delete_othersFile_returns403() throws Exception {
        String body = issue(UPLOADER).andReturn().getResponse().getContentAsString();
        long fileId = ((Number) JsonPath.read(body, "$.data.fileId")).longValue();
        String fileUrl = JsonPath.read(body, "$.data.fileUrl");

        deleteByUrl(OTHER_USER, fileUrl)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NO_PERMISSION"));

        assertThat(uploadedFileRepository.findById(fileId)).isPresent();
    }

    @Test
    @DisplayName("이 서버가 발급한 URL이 아니면 404 FILE_NOT_FOUND다")
    void delete_foreignUrl_returns404() throws Exception {
        deleteByUrl(UPLOADER, "https://evil.example.com/profile/abc.png")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FILE_NOT_FOUND"));
    }

    @Test
    @DisplayName("fileUrl이 없으면 400 VALIDATION_FAILED다")
    void delete_withoutFileUrl_returns400() throws Exception {
        mockMvc.perform(delete(BASE).header("Authorization", bearer(UPLOADER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("fileUrl: 필수 파라미터입니다."));
    }

    private ResultActions issue(long userId) throws Exception {
        return mockMvc.perform(post(BASE + "/presigned-url").header("Authorization", bearer(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"domain\":\"PROFILE_IMAGE\",\"fileName\":\"me.png\",\"contentType\":\"image/png\"}"));
    }

    // 프론트처럼 encodeURIComponent 한 값을 쿼리에 넣는다. URI로 넘겨야 MockMvc가 한 번 더 인코딩하지 않는다.
    private ResultActions deleteByUrl(long userId, String fileUrl) throws Exception {
        String encoded = URLEncoder.encode(fileUrl, StandardCharsets.UTF_8);
        return mockMvc.perform(delete(URI.create(BASE + "?fileUrl=" + encoded))
                .header("Authorization", bearer(userId)));
    }

    private String bearer(long userId) {
        return "Bearer " + jwtProvider.generateToken(userId);
    }
}

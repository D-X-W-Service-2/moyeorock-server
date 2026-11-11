package com.moyeorock.domain.practice.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.domain.practice.repository.PracticeRecordingRepository;
import com.moyeorock.domain.song.entity.Song;
import com.moyeorock.domain.song.repository.SongRepository;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.file.client.FileStorageClient;
import com.moyeorock.global.file.dto.response.PresignedUrlResponse;
import com.moyeorock.global.file.enums.UploadDomain;
import com.moyeorock.global.file.service.FileService;
import com.moyeorock.global.security.JwtProvider;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 연습 녹음 API 5개의 상태 코드·응답 모양(노션 API 초안 "연습"). 클래스에 {@code @Transactional}이 있어 요청 트랜잭션이
 * 커밋되지 않으므로 분석은 실행기로 넘어가지 않는다 — 분석 흐름은 PracticeAnalysisFlowTest가 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class PracticeRecordingApiTest {

    private static final String BASE = "/v1/practice-recordings";
    private static final long ME = 9_100_001L;
    private static final long OTHER = 9_100_002L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private FileService fileService;

    @Autowired
    private FileStorageClient fileStorageClient;

    @Autowired
    private PracticeRecordingRepository recordingRepository;

    @Autowired
    private SongRepository songRepository;

    private long songId;

    @BeforeEach
    void saveSong() {
        songId = songRepository.save(Song.create("예뻤어", "DAY6", Genre.ROCK, null, null, null,
                "local-ce9924cfa2b2a7d2d4a37f46")).getId();
    }

    @Test
    @DisplayName("토큰 없이 호출하면 5개 모두 401이다")
    void withoutToken_returns401() throws Exception {
        mockMvc.perform(post(BASE)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/users/me/practice-recordings")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE + "/1/retry")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(BASE + "/1")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("분석 요청: 202와 SUBMITTED, analyzedAt은 null")
    void create_returns202Submitted() throws Exception {
        create(ME, "11/9 보컬 연습 - 너의 의미", uploadAudio(ME))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.title").value("11/9 보컬 연습 - 너의 의미"))
                .andExpect(jsonPath("$.data.part").value("VOCAL"))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data.analyzedAt").value(nullValue()));
    }

    @Test
    @DisplayName("분석 요청: 제목 빈칸·101자, audioUrl 빈칸, 세션 없음·없는 세션 값, 곡 없음이면 400 VALIDATION_FAILED")
    void create_returns400_forInvalidInput() throws Exception {
        String audioUrl = uploadAudio(ME);
        create(ME, " ", audioUrl).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        create(ME, "가".repeat(101), audioUrl).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        create(ME, "연습", "").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        createRaw(ME, "{\"title\":\"연습\",\"songId\":" + songId + ",\"audioUrl\":\"" + audioUrl + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("part: 세션은 필수입니다."));
        createRaw(ME, "{\"title\":\"연습\",\"part\":\"SAXOPHONE\",\"songId\":" + songId + ",\"audioUrl\":\""
                + audioUrl + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        createRaw(ME, "{\"title\":\"연습\",\"part\":\"VOCAL\",\"audioUrl\":\"" + audioUrl + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("songId: 곡은 필수입니다."));
    }

    @Test
    @DisplayName("분석 요청: 없는 곡이면 404 SONG_NOT_FOUND")
    void create_returns404_forUnknownSong() throws Exception {
        createRaw(ME, "{\"title\":\"연습\",\"part\":\"VOCAL\",\"songId\":" + (songId + 1_000_000)
                + ",\"audioUrl\":\"" + uploadAudio(ME) + "\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SONG_NOT_FOUND"));
    }

    @Test
    @DisplayName("분석 요청: 남이 올린 파일이면 400 VALIDATION_FAILED와 audioUrl 사유")
    void create_returns400_forOthersAudio() throws Exception {
        create(ME, "연습", uploadAudio(OTHER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("audioUrl: 내가 올린 연습 녹음 파일이 아닙니다."));
    }

    @Test
    @DisplayName("분석 요청: 진행 중인 내 녹음이 있으면 409 PRACTICE_ANALYSIS_IN_PROGRESS")
    void create_returns409_whenInProgress() throws Exception {
        create(ME, "첫 번째", uploadAudio(ME)).andExpect(status().isAccepted());

        create(ME, "두 번째", uploadAudio(ME))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRACTICE_ANALYSIS_IN_PROGRESS"));
    }

    @Test
    @DisplayName("내 목록: 200, 내 녹음만 페이지 모양으로")
    void getMyRecordings_returnsPage() throws Exception {
        long id = createAndGetId(ME, "내 연습");
        createAndGetId(OTHER, "남의 연습");

        mockMvc.perform(get("/v1/users/me/practice-recordings").header("Authorization", bearer(ME)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(id))
                .andExpect(jsonPath("$.data.content[0].status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.content[0].part").value("VOCAL"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.last").value(true));
    }

    @Test
    @DisplayName("상세: 200, 재생 URL과 빈 피드백 / 남의 녹음은 404 PRACTICE_RECORDING_NOT_FOUND")
    void getDetail_returnsMine_and404ForOthers() throws Exception {
        long id = createAndGetId(ME, "내 연습");

        mockMvc.perform(get(BASE + "/" + id).header("Authorization", bearer(ME)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.part").value("VOCAL"))
                .andExpect(jsonPath("$.data.audioUrl").value(containsString("/practice/")))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.failureReason").value(nullValue()))
                .andExpect(jsonPath("$.data.feedbacks.length()").value(0));

        mockMvc.perform(get(BASE + "/" + id).header("Authorization", bearer(OTHER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRACTICE_RECORDING_NOT_FOUND"));
    }

    @Test
    @DisplayName("다시 요청: 실패한 녹음이 아니면 409 INVALID_STATE, 실패한 녹음은 202와 SUBMITTED")
    void retry_onlyFailedRecording() throws Exception {
        long id = createAndGetId(ME, "내 연습");

        retry(ME, id).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE"));

        recordingRepository.findById(id).orElseThrow().fail("이전 실패");
        recordingRepository.flush();

        retry(ME, id).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));
        mockMvc.perform(get(BASE + "/" + id).header("Authorization", bearer(ME)))
                .andExpect(jsonPath("$.data.failureReason").value(nullValue()));
    }

    @Test
    @DisplayName("삭제: 200 data null, 이후 상세는 404 / 남의 녹음은 404")
    void delete_removesRecording() throws Exception {
        long id = createAndGetId(ME, "내 연습");

        mockMvc.perform(delete(BASE + "/" + id).header("Authorization", bearer(OTHER)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(BASE + "/" + id).header("Authorization", bearer(ME)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(nullValue()));
        mockMvc.perform(get(BASE + "/" + id).header("Authorization", bearer(ME)))
                .andExpect(status().isNotFound());
    }

    private ResultActions create(long userId, String title, String audioUrl) throws Exception {
        return mockMvc.perform(post(BASE).header("Authorization", bearer(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + title + "\",\"part\":\"VOCAL\",\"songId\":" + songId
                        + ",\"audioUrl\":\"" + audioUrl + "\"}"));
    }

    private ResultActions createRaw(long userId, String json) throws Exception {
        return mockMvc.perform(post(BASE).header("Authorization", bearer(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions retry(long userId, long id) throws Exception {
        return mockMvc.perform(post(BASE + "/" + id + "/retry").header("Authorization", bearer(userId)));
    }

    private long createAndGetId(long userId, String title) throws Exception {
        String body = create(userId, title, uploadAudio(userId)).andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    // 업로드 URL을 발급받고 실제로 파일을 올린 것처럼 저장소에 써 둔다(테스트 설정은 로컬 저장).
    private String uploadAudio(long userId) {
        PresignedUrlResponse issued = fileService.issuePresignedUrl(UploadDomain.PRACTICE_AUDIO, "practice.m4a",
                "audio/mp4", userId);
        String fileKey = fileStorageClient.toFileKey(issued.fileUrl()).orElseThrow();
        fileStorageClient.write(fileKey, new ByteArrayInputStream(new byte[]{1, 2, 3}));
        return issued.fileUrl();
    }

    private String bearer(long userId) {
        return "Bearer " + jwtProvider.generateToken(userId);
    }
}

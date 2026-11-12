package com.moyeorock.domain.practice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServiceUnavailable;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import com.moyeorock.domain.practice.config.PracticeProperties;
import com.moyeorock.global.common.enums.Instrument;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

// 실제 AI 서버를 호출하지 않는다. MockRestServiceServer로 연습 피드백 API를 스텁한다(AiSongClientTest와 같은 방식).
class AiPracticeAnalysisClientTest {

    private static final String BASE_URL = "http://ai.example";
    private static final String FEEDBACK_URL = BASE_URL + "/v1/rehearsals/feedback";
    private static final AnalysisRequest REQUEST =
            new AnalysisRequest("local-ce9924cfa2b2a7d2d4a37f46", new byte[]{1, 2, 3}, "abc.m4a", Instrument.VOCAL);

    private MockRestServiceServer server;

    private AiPracticeAnalysisClient client(String baseUrl) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new AiPracticeAnalysisClient(
                new PracticeProperties.Ai("ai", 0, baseUrl, "ai-key", 180), builder.build());
    }

    @Test
    @DisplayName("externalId·part·Base64 녹음·파일 이름을 Bearer 키와 함께 보내고, 분석 이벤트를 피드백으로 바꾼다")
    void analyze_sendsRequest_andMapsEvents() {
        AiPracticeAnalysisClient client = client(BASE_URL);
        server.expect(requestTo(FEEDBACK_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer ai-key"))
                .andExpect(content().json("""
                        {"externalId":"local-ce9924cfa2b2a7d2d4a37f46","performanceAudioBase64":"AQID",
                         "performanceFilename":"abc.m4a","part":"VOCAL"}""", JsonCompareMode.STRICT))
                .andRespond(withSuccess("""
                        {"status":"analyzed",
                         "events":[{"start":13.0,"end":16.2,"type":"rhythm_mismatch","message":"원곡보다 박자가 빠른 구간이에요.","score":0.987,"reference_start":20.056,"reference_end":23.0},
                                   {"start":40.4,"end":42.0,"type":"missing_or_quiet","score":0.5,"reference_start":65.5,"reference_end":70.1}],
                         "alignment_similarity":0.98,"alignment_method":"chroma_onset",
                         "transposition_semitones":0,"sensitivity":"conservative"}""", MediaType.APPLICATION_JSON));

        List<AnalysisFeedback> result = client.analyze(REQUEST);

        assertThat(result).containsExactly(
                new AnalysisFeedback(13, 17, "VOCAL", "원곡 0:20~0:23: 원곡보다 박자가 빠른 구간이에요."),
                new AnalysisFeedback(40, 42, "VOCAL", "원곡 1:05~1:11: 연주가 빠졌거나 소리가 작은 구간이에요."));
        server.verify();
    }

    @Test
    @DisplayName("base-url 끝에 /가 있어도 //가 되지 않는다")
    void analyze_joinsPathWithoutDoubleSlash() {
        AiPracticeAnalysisClient client = client(BASE_URL + "/");
        server.expect(requestTo(FEEDBACK_URL))
                .andExpect(content().json("{\"part\":\"VOCAL\"}"))
                .andRespond(withSuccess("{\"status\":\"analyzed\",\"events\":[]}", MediaType.APPLICATION_JSON));

        assertThat(client.analyze(REQUEST)).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("원곡 구간이 없는 event는 구간 없는 문장이 되고, 녹음 구간이 없는 event는 버린다")
    void analyze_handlesMissingTimes() {
        AiPracticeAnalysisClient client = client(BASE_URL);
        server.expect(requestTo(FEEDBACK_URL))
                .andRespond(withSuccess("""
                        {"status":"analyzed","events":[{"start":1.5,"end":3.0},{"start":null,"end":5.0}]}""",
                        MediaType.APPLICATION_JSON));

        assertThat(client.analyze(REQUEST))
                .containsExactly(new AnalysisFeedback(1, 3, "VOCAL", "원곡과 다르게 연주한 구간이에요."));
    }

    @Test
    @DisplayName("review_required(원곡과 맞추지 못함)는 사유가 정해진 실패다")
    void analyze_throwsReviewRequired() {
        AiPracticeAnalysisClient client = client(BASE_URL);
        server.expect(requestTo(FEEDBACK_URL))
                .andRespond(withSuccess("{\"status\":\"review_required\",\"events\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.analyze(REQUEST))
                .isInstanceOf(PracticeAnalysisException.class)
                .hasMessage(PracticeAnalysisException.REVIEW_REQUIRED);
    }

    @Test
    @DisplayName("알 수 없는 status는 사유 없는 실패다")
    void analyze_throws_onUnknownStatus() {
        AiPracticeAnalysisClient client = client(BASE_URL);
        server.expect(requestTo(FEEDBACK_URL))
                .andRespond(withSuccess("{\"status\":\"queued\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.analyze(REQUEST))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(PracticeAnalysisException.class);
    }

    @Test
    @DisplayName("404와 'Feedback is not available for this song'이면 지원하지 않는 곡이다")
    void analyze_throwsUnsupportedSong_on404WithMessage() {
        AiPracticeAnalysisClient client = client(BASE_URL);
        server.expect(requestTo(FEEDBACK_URL))
                .andRespond(withResourceNotFound().contentType(MediaType.APPLICATION_JSON)
                        .body("{\"detail\":\"Feedback is not available for this song\"}"));

        assertThatThrownBy(() -> client.analyze(REQUEST))
                .isInstanceOf(PracticeAnalysisException.class)
                .hasMessage(PracticeAnalysisException.UNSUPPORTED_SONG);
    }

    @Test
    @DisplayName("문장이 다른 404(주소 설정 문제)와 401(키 문제)은 사유로 바꾸지 않고 그대로 던진다")
    void analyze_rethrows_onConfigErrors() {
        AiPracticeAnalysisClient client = client(BASE_URL);
        server.expect(requestTo(FEEDBACK_URL)).andRespond(withResourceNotFound().body("Not Found"));
        server.expect(requestTo(FEEDBACK_URL)).andRespond(withUnauthorizedRequest());

        assertThatThrownBy(() -> client.analyze(REQUEST)).isInstanceOf(HttpClientErrorException.NotFound.class);
        assertThatThrownBy(() -> client.analyze(REQUEST)).isInstanceOf(HttpClientErrorException.Unauthorized.class);
    }

    @Test
    @DisplayName("400(녹음 길이·형식 문제)은 녹음을 확인하라는 사유, 503(원곡 저장소 장애)은 잠시 후 다시 요청하라는 사유다")
    void analyze_mapsBadRequestAndServiceUnavailable() {
        AiPracticeAnalysisClient client = client(BASE_URL);
        server.expect(requestTo(FEEDBACK_URL)).andRespond(withBadRequest());
        server.expect(requestTo(FEEDBACK_URL)).andRespond(withServiceUnavailable());

        assertThatThrownBy(() -> client.analyze(REQUEST))
                .isInstanceOf(PracticeAnalysisException.class)
                .hasMessage(PracticeAnalysisException.INVALID_RECORDING);
        assertThatThrownBy(() -> client.analyze(REQUEST))
                .isInstanceOf(PracticeAnalysisException.class)
                .hasMessage(PracticeAnalysisException.AI_UNAVAILABLE);
    }

    @Test
    @DisplayName("type=ai인데 주소나 키가 없으면 부팅 때 실패한다")
    void constructor_throws_whenNotConfigured() {
        assertThatThrownBy(() -> new AiPracticeAnalysisClient(
                new PracticeProperties.Ai("ai", 0, "", "ai-key", 180), RestClient.create()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AiPracticeAnalysisClient(
                new PracticeProperties.Ai("ai", 0, BASE_URL, null, 180), RestClient.create()))
                .isInstanceOf(IllegalStateException.class);
    }
}

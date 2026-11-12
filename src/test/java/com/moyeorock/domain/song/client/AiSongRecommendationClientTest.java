package com.moyeorock.domain.song.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import com.moyeorock.domain.song.config.AiRecommendProperties;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Level;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

// 실제 AI 서버를 호출하지 않는다. MockRestServiceServer로 곡 추천 호출을 스텁한다.
class AiSongRecommendationClientTest {

    private static final String BASE_URL = "http://ai.example";
    private static final String URL = BASE_URL + "/v1/recommendations/songs";

    private MockRestServiceServer server;

    private AiSongRecommendationClient client(String baseUrl, String apiKey) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new AiSongRecommendationClient(new AiRecommendProperties(baseUrl, apiKey), builder.build());
    }

    private AiSongRecommendRequest request() {
        return AiSongRecommendRequest.forTeam(List.of(Genre.ROCK),
                List.of(new AiSongRecommendRequest.Member(Instrument.BASS, Level.NOVICE)), 20);
    }

    @Test
    @DisplayName("mode·장르·세션 실력·limit을 POST 본문으로 보내고 X-AI-Recommend-Key 헤더를 붙이며, 곡과 점수를 돌려준다")
    void recommend_sendsRequestAndParsesResponse() {
        AiSongRecommendationClient client = client(BASE_URL, "ai-key");
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-AI-Recommend-Key", "ai-key"))
                .andExpect(headerDoesNotExist("Authorization"))
                .andExpect(header("Content-Type", "application/json"))
                .andExpect(content().json("""
                        {"mode":"TEAM","genres":["ROCK"],"members":[{"part":"BASS","level":"NOVICE"}],"limit":20}"""))
                .andRespond(withSuccess("""
                        {"songs":[{"externalId":"local-abc","score":0.875},{"externalId":"local-def","score":0.812}],
                         "ranker":"content_rule"}""", MediaType.APPLICATION_JSON));

        AiSongRecommendResponse response = client.recommend(request());

        assertThat(response.songs()).containsExactly(
                new AiSongRecommendResponse.Item("local-abc", 0.875),
                new AiSongRecommendResponse.Item("local-def", 0.812));
        assertThat(response.ranker()).isEqualTo("content_rule");
        server.verify();
    }

    @Test
    @DisplayName("주소 끝에 /가 있어도 경로가 //로 붙지 않는다")
    void recommend_trailingSlashInBaseUrl() {
        AiSongRecommendationClient client = client(BASE_URL + "/", "ai-key");
        server.expect(requestTo(URL))
                .andRespond(withSuccess("{\"songs\":[]}", MediaType.APPLICATION_JSON));

        assertThat(client.recommend(request()).songs()).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("키가 없으면 X-AI-Recommend-Key 헤더를 보내지 않는다")
    void recommend_withoutApiKey_sendsNoKeyHeader() {
        AiSongRecommendationClient client = client(BASE_URL, "");
        server.expect(requestTo(URL))
                .andExpect(headerDoesNotExist("X-AI-Recommend-Key"))
                .andRespond(withSuccess("{\"songs\":[]}", MediaType.APPLICATION_JSON));

        assertThat(client.recommend(request()).songs()).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("점수가 빠진 항목도 읽는다(버리는 일은 Service가 한다)")
    void recommend_acceptsItemWithoutScore() {
        AiSongRecommendationClient client = client(BASE_URL, "ai-key");
        server.expect(requestTo(URL))
                .andRespond(withSuccess("{\"songs\":[{\"externalId\":\"local-abc\"}]}", MediaType.APPLICATION_JSON));

        assertThat(client.recommend(request()).songs())
                .singleElement().isEqualTo(new AiSongRecommendResponse.Item("local-abc", null));
    }

    @Test
    @DisplayName("4xx·5xx 응답은 RestClientException으로 그대로 전파한다")
    void recommend_propagatesErrorResponses() {
        AiSongRecommendationClient client = client(BASE_URL, "ai-key");
        server.expect(requestTo(URL)).andRespond(withUnauthorizedRequest());
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.recommend(request())).isInstanceOf(HttpClientErrorException.class);
        assertThatThrownBy(() -> client.recommend(request())).isInstanceOf(HttpServerErrorException.class);
    }

    @Test
    @DisplayName("songs가 없거나 본문이 비면 계약 위반으로 IllegalStateException이다")
    void recommend_missingSongs_throws() {
        AiSongRecommendationClient client = client(BASE_URL, "ai-key");
        server.expect(requestTo(URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL)).andRespond(withSuccess());

        assertThatThrownBy(() -> client.recommend(request())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> client.recommend(request())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("base-url이 비어 있으면 설정되지 않은 것으로 본다")
    void isConfigured() {
        assertThat(client(BASE_URL, "ai-key").isConfigured()).isTrue();
        assertThat(client("", "ai-key").isConfigured()).isFalse();
        assertThat(client(null, "ai-key").isConfigured()).isFalse();
    }
}

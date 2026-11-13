package com.moyeorock.domain.team.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import com.moyeorock.domain.song.config.AiServerProperties;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Level;
import com.moyeorock.global.common.enums.Region;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

// 실제 AI 서버를 호출하지 않는다. MockRestServiceServer로 팀원 추천 호출을 스텁한다.
class AiMemberRecommendationClientTest {

    private static final String BASE_URL = "http://ai.example";
    private static final String URL = BASE_URL + "/v1/recommendations/members";

    private MockRestServiceServer server;

    private AiMemberRecommendationClient client(String baseUrl, String apiKey) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new AiMemberRecommendationClient(new AiServerProperties(baseUrl, apiKey), builder.build());
    }

    private AiRecommendRequest request() {
        return new AiRecommendRequest(
                new AiRecommendRequest.Team(5L, Region.SEOUL, List.of(Genre.ROCK, Genre.POP)),
                Instrument.DRUM,
                List.of(new AiRecommendRequest.Member(3L, Instrument.VOCAL, Level.ADVANCED)),
                List.of(new AiRecommendRequest.Candidate(30L, Region.SEOUL, List.of(Genre.ROCK), Level.INTERMEDIATE)));
    }

    @Test
    @DisplayName("팀·악기·팀원·후보를 POST 본문으로 보내고 Bearer 키를 붙이며, 점수와 근거를 돌려준다")
    void recommend_sendsRequestAndParsesResponse() {
        AiMemberRecommendationClient client = client(BASE_URL, "ai-key");
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer ai-key"))
                .andExpect(header("Content-Type", "application/json"))
                .andExpect(jsonPath("$.team.id").value(5))
                .andExpect(jsonPath("$.team.region").value("SEOUL"))
                .andExpect(jsonPath("$.team.genres[0]").value("ROCK"))
                .andExpect(jsonPath("$.instrument").value("DRUM"))
                .andExpect(jsonPath("$.members[0].userId").value(3))
                .andExpect(jsonPath("$.members[0].instrument").value("VOCAL"))
                .andExpect(jsonPath("$.members[0].level").value("ADVANCED"))
                .andExpect(jsonPath("$.candidates[0].userId").value(30))
                .andExpect(jsonPath("$.candidates[0].level").value("INTERMEDIATE"))
                .andRespond(withSuccess("""
                        {"candidates":[{"userId":30,"score":0.87,"reasons":["장르 일치: ROCK","활동 지역 동일"]}]}""",
                        MediaType.APPLICATION_JSON));

        AiRecommendResponse response = client.recommend(request());

        assertThat(response.candidates()).singleElement().isEqualTo(
                new AiRecommendResponse.Item(30L, 0.87, List.of("장르 일치: ROCK", "활동 지역 동일")));
        server.verify();
    }

    @Test
    @DisplayName("키가 없으면 Authorization 헤더를 보내지 않는다")
    void recommend_withoutApiKey_sendsNoAuthorizationHeader() {
        AiMemberRecommendationClient client = client(BASE_URL, "");
        server.expect(requestTo(URL))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withSuccess("{\"candidates\":[]}", MediaType.APPLICATION_JSON));

        assertThat(client.recommend(request()).candidates()).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("요청 본문의 null 값(온보딩 전 지역 등)도 그대로 직렬화한다")
    void recommend_serializesNullFields() {
        AiMemberRecommendationClient client = client(BASE_URL, "ai-key");
        server.expect(requestTo(URL))
                .andExpect(content().json("""
                        {"team":{"id":5,"region":null,"genres":[]},"instrument":"DRUM","members":[],
                         "candidates":[{"userId":30,"region":null,"genres":null,"level":"NOVICE"}]}"""))
                .andRespond(withSuccess("{\"candidates\":[]}", MediaType.APPLICATION_JSON));

        client.recommend(new AiRecommendRequest(
                new AiRecommendRequest.Team(5L, null, List.of()), Instrument.DRUM, List.of(),
                List.of(new AiRecommendRequest.Candidate(30L, null, null, Level.NOVICE))));

        server.verify();
    }

    @Test
    @DisplayName("점수나 근거가 빠진 항목도 읽는다")
    void recommend_acceptsItemWithoutScoreOrReasons() {
        AiMemberRecommendationClient client = client(BASE_URL, "ai-key");
        server.expect(requestTo(URL))
                .andRespond(withSuccess("{\"candidates\":[{\"userId\":30}]}", MediaType.APPLICATION_JSON));

        assertThat(client.recommend(request()).candidates())
                .singleElement().isEqualTo(new AiRecommendResponse.Item(30L, null, null));
    }

    @Test
    @DisplayName("4xx·5xx 응답은 RestClientException으로 그대로 전파한다")
    void recommend_propagatesErrorResponses() {
        AiMemberRecommendationClient client = client(BASE_URL, "ai-key");
        server.expect(requestTo(URL)).andRespond(withUnauthorizedRequest());
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.recommend(request())).isInstanceOf(HttpClientErrorException.class);
        assertThatThrownBy(() -> client.recommend(request())).isInstanceOf(HttpServerErrorException.class);
    }

    @Test
    @DisplayName("candidates가 없거나 본문이 비면 계약 위반으로 IllegalStateException이다")
    void recommend_missingCandidates_throws() {
        AiMemberRecommendationClient client = client(BASE_URL, "ai-key");
        server.expect(requestTo(URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL)).andRespond(withSuccess());

        assertThatThrownBy(() -> client.recommend(request())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> client.recommend(request())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("base-url이 비어 있으면 설정되지 않은 것으로 본다")
    void isConfigured() {
        assertThat(client(BASE_URL, "k").isConfigured()).isTrue();
        assertThat(client("", "k").isConfigured()).isFalse();
        assertThat(client(null, "k").isConfigured()).isFalse();
    }
}

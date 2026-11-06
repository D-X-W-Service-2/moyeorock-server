package com.moyeorock.domain.song.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import com.moyeorock.domain.song.config.AiServerProperties;
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

// 실제 AI 서버를 호출하지 않는다. MockRestServiceServer로 카탈로그 조회를 스텁한다.
class AiSongClientTest {

    private static final String BASE_URL = "http://ai.example";

    private MockRestServiceServer server;

    private AiSongClient client(String baseUrl, String apiKey) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new AiSongClient(new AiServerProperties(baseUrl, apiKey), builder.build());
    }

    @Test
    @DisplayName("첫 페이지는 cursor 없이 limit만 보내고 Bearer 키를 붙이며, 곡 목록과 nextCursor를 돌려준다")
    void fetchSongs_firstPage() {
        AiSongClient client = client(BASE_URL, "ai-key");
        server.expect(requestTo(BASE_URL + "/v1/songs?limit=500"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer ai-key"))
                .andRespond(withSuccess("""
                        {"songs":[{"externalId":"youtube:Lb3mQdm-UHs","title":"고민중독","artist":"QWER",
                          "genre":"ROCK","songKey":"C","bpm":136,
                          "difficulty":[{"part":"VOCAL","level":"ADVANCED"}]}],
                         "nextCursor":"c1"}""", MediaType.APPLICATION_JSON));

        AiSongPage page = client.fetchSongs(null, 500);

        assertThat(page.nextCursor()).isEqualTo("c1");
        assertThat(page.songs()).singleElement().isEqualTo(new AiSongPage.Item("youtube:Lb3mQdm-UHs", "고민중독",
                "QWER", Genre.ROCK, "C", 136,
                List.of(new AiSongPage.Difficulty(Instrument.VOCAL, Level.ADVANCED))));
        server.verify();
    }

    @Test
    @DisplayName("커서는 +·/·= 까지 인코딩해서 보낸다")
    void fetchSongs_encodesCursor() {
        AiSongClient client = client(BASE_URL, "ai-key");
        server.expect(requestTo(BASE_URL + "/v1/songs?limit=500&cursor=a%2Bb%2Fc%3D"))
                .andRespond(withSuccess("{\"songs\":[],\"nextCursor\":null}", MediaType.APPLICATION_JSON));

        AiSongPage page = client.fetchSongs("a+b/c=", 500);

        assertThat(page.songs()).isEmpty();
        assertThat(page.nextCursor()).isNull();
        server.verify();
    }

    @Test
    @DisplayName("키가 비어 있으면 Authorization 헤더를 붙이지 않는다")
    void fetchSongs_withoutApiKey() {
        AiSongClient client = client(BASE_URL, "");
        server.expect(requestTo(BASE_URL + "/v1/songs?limit=500"))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withSuccess("{\"songs\":[]}", MediaType.APPLICATION_JSON));

        client.fetchSongs(null, 500);

        server.verify();
    }

    @Test
    @DisplayName("AI 서버 4xx·5xx는 RestClientException 그대로 던진다")
    void fetchSongs_propagatesHttpErrors() {
        AiSongClient client = client(BASE_URL, "ai-key");
        server.expect(requestTo(BASE_URL + "/v1/songs?limit=500")).andRespond(withServerError());
        assertThatThrownBy(() -> client.fetchSongs(null, 500)).isInstanceOf(HttpServerErrorException.class);

        AiSongClient another = client(BASE_URL, "wrong-key");
        server.expect(requestTo(BASE_URL + "/v1/songs?limit=500")).andRespond(withUnauthorizedRequest());
        assertThatThrownBy(() -> another.fetchSongs(null, 500)).isInstanceOf(HttpClientErrorException.class);
    }

    @Test
    @DisplayName("응답에 songs가 없으면 계약 위반으로 IllegalStateException이다")
    void fetchSongs_throws_whenSongsMissing() {
        AiSongClient client = client(BASE_URL, "ai-key");
        server.expect(requestTo(BASE_URL + "/v1/songs?limit=500"))
                .andRespond(withSuccess("{\"nextCursor\":null}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchSongs(null, 500)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("base-url이 비어 있으면 설정되지 않은 것으로 본다")
    void isConfigured() {
        assertThat(client(BASE_URL, "").isConfigured()).isTrue();
        assertThat(client("", "ai-key").isConfigured()).isFalse();
        assertThat(client(null, "ai-key").isConfigured()).isFalse();
    }
}

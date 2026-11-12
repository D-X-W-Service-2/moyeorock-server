package com.moyeorock.domain.song.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Level;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AiRecommendationClientTest {

    @Test
    void sendsProfileOnlyAndReadsAiSelectedSongs() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> requestKey = new AtomicReference<>();
        server.createContext("/v1/recommendations/songs", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            requestKey.set(exchange.getRequestHeaders().getFirst("X-AI-Recommend-Key"));
            byte[] response = "{\"songs\":[{\"externalId\":\"local-1\",\"score\":0.9}],\"ranker\":\"content_rule\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.start();
        try {
            var client = new AiRecommendationClient("http://127.0.0.1:" + server.getAddress().getPort(), "secret");

            var result = client.recommend("PERSONAL", List.of(Genre.ROCK),
                    List.of(new AiRecommendationClient.MemberLevel(Instrument.BASS, Level.NOVICE)), 20);

            assertThat(result.songs()).containsExactly(new AiRecommendationClient.RecommendedSong("local-1", 0.9));
            assertThat(result.ranker()).isEqualTo("content_rule");
            assertThat(requestKey.get()).isEqualTo("secret");
            assertThat(requestBody.get()).contains("\"mode\":\"PERSONAL\"", "\"limit\":20");
            assertThat(requestBody.get()).doesNotContain("songs", "externalId", "userId");
        } finally {
            server.stop(0);
        }
    }
}

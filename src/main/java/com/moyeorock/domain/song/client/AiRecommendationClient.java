package com.moyeorock.domain.song.client;

import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Level;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Sends profile features only; the AI service selects songs from its own catalog. */
@Component
@Slf4j
public class AiRecommendationClient {

    private final String baseUrl;
    private final String apiKey;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public AiRecommendationClient(@Value("${ai.recommend-url:}") String baseUrl,
                                  @Value("${ai.recommend-api-key:}") String apiKey) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }

    public record MemberLevel(Instrument part, Level level) {
    }

    private record RecommendRequest(String mode, List<Genre> genres, List<MemberLevel> members,
                                    int limit) {
    }

    public record RecommendedSong(String externalId, double score) {
    }

    private record RecommendResponse(List<RecommendedSong> songs, String ranker) {
    }

    public record RecommendResult(List<RecommendedSong> songs, String ranker) {
    }

    public RecommendResult recommend(String mode, List<Genre> genres, List<MemberLevel> members,
                                     int limit) {
        if (baseUrl == null || baseUrl.isBlank() || apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("AI recommendation service is not configured");
        }
        try {
            String json = mapper.writeValueAsString(new RecommendRequest(mode, genres, members, limit));
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl.replaceAll("/+$", "")
                            + "/v1/recommendations/songs"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .header("X-AI-Recommend-Key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("AI HTTP " + response.statusCode());
            }
            RecommendResponse body = mapper.readValue(response.body(), RecommendResponse.class);
            if (body == null || body.songs() == null || body.songs().size() > limit) {
                throw new IllegalStateException("AI response has an invalid song list");
            }
            Set<String> seen = new HashSet<>();
            List<RecommendedSong> songs = new ArrayList<>();
            for (RecommendedSong item : body.songs()) {
                if (item == null || item.externalId() == null || item.externalId().isBlank()
                        || !Double.isFinite(item.score()) || item.score() < 0 || item.score() > 1
                        || !seen.add(item.externalId())) {
                    throw new IllegalStateException("AI response contains an invalid or duplicate song");
                }
                songs.add(item);
            }
            return new RecommendResult(List.copyOf(songs),
                    body.ranker() == null || body.ranker().isBlank() ? "AI_UNKNOWN" : body.ranker());
        } catch (Exception e) {
            log.warn("AI recommendation unavailable: {}", e.toString());
            throw new IllegalStateException("AI recommendation unavailable", e);
        }
    }
}

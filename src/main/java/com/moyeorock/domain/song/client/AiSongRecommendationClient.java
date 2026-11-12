package com.moyeorock.domain.song.client;

import com.moyeorock.domain.song.config.AiRecommendProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * AI 서버 곡 추천 호출(이슈 #171). 프론트 요청 한 건마다 동기로 부른다. 팀원 추천({@code AiMemberRecommendationClient})과
 * 같은 방식이고, 다른 점은 경로와 인증 헤더({@code X-AI-Recommend-Key})다.
 *
 * <p>호출 실패(4xx·5xx·타임아웃)는 {@link org.springframework.web.client.RestClientException}을 그대로 던진다.
 * 에러 응답(SONG_RECOMMENDATION_UNAVAILABLE)으로 바꾸는 일은 SongRecommendationService가 한다.
 */
@Component
public class AiSongRecommendationClient {

    static final String RECOMMEND_PATH = "/v1/recommendations/songs";
    static final String API_KEY_HEADER = "X-AI-Recommend-Key";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    private final AiRecommendProperties properties;
    private final RestClient restClient;

    @Autowired
    public AiSongRecommendationClient(AiRecommendProperties properties) {
        this(properties, RestClient.builder().requestFactory(defaultRequestFactory()).build());
    }

    // 테스트용: MockRestServiceServer를 바인딩한 RestClient 주입
    AiSongRecommendationClient(AiRecommendProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    /** base-url이 없으면 부를 곳이 없다. AI 서버 없이 띄운 환경에서는 곡 추천을 쓸 수 없다. */
    public boolean isConfigured() {
        return StringUtils.hasText(properties.baseUrl());
    }

    /**
     * 곡 추천을 요청한다. 응답 본문이 없거나 {@code songs}가 없으면 계약 위반이라 {@link IllegalStateException}이다.
     * 고른 곡이 없으면 빈 배열로 와야 한다.
     */
    public AiSongRecommendResponse recommend(AiSongRecommendRequest request) {
        AiSongRecommendResponse response = restClient.post()
                .uri(recommendUri())
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    if (StringUtils.hasText(properties.apiKey())) {
                        headers.set(API_KEY_HEADER, properties.apiKey());
                    }
                })
                .body(request)
                .retrieve()
                .body(AiSongRecommendResponse.class);
        if (response == null || response.songs() == null) {
            throw new IllegalStateException("AI 서버 곡 추천 응답에 songs가 없습니다.");
        }
        return response;
    }

    // 주소 끝에 /가 있어도 //가 되지 않게 붙인다(AiPracticeAnalysisClient와 같은 방식).
    private URI recommendUri() {
        return UriComponentsBuilder.fromUriString(properties.baseUrl()).path(RECOMMEND_PATH).build().toUri();
    }

    private static JdkClientHttpRequestFactory defaultRequestFactory() {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }
}

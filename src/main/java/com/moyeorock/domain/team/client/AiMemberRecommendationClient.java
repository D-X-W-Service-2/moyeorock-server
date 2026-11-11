package com.moyeorock.domain.team.client;

import com.moyeorock.domain.song.config.AiServerProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * AI 서버 팀원 추천 호출(이슈 #150). 프론트 요청 한 건마다 동기로 부른다 — 곡 카탈로그(AiSongClient)처럼 미리 받아 두는 게 아니다.
 * Service 안에 두면 테스트에서 AI 서버를 끊을 수 없어 별도 클래스로 둔다(KakaoOAuthClient·AiSongClient와 같은 방식, 인터페이스 없음).
 * 주소·키는 곡 동기화와 같은 {@link AiServerProperties}를 쓴다.
 *
 * <p>호출 실패(4xx·5xx·타임아웃)는 {@link org.springframework.web.client.RestClientException}을 그대로 던진다.
 * 에러 응답(RECOMMENDATION_UNAVAILABLE)으로 바꾸는 일은 TeamService가 한다.
 */
@Component
public class AiMemberRecommendationClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);
    private static final String RECOMMEND_PATH = "/v1/recommendations/members";

    private final AiServerProperties properties;
    private final RestClient restClient;

    @Autowired
    public AiMemberRecommendationClient(AiServerProperties properties) {
        this(properties, RestClient.builder().requestFactory(defaultRequestFactory()).build());
    }

    // 테스트용: MockRestServiceServer를 바인딩한 RestClient 주입
    AiMemberRecommendationClient(AiServerProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    /** base-url이 없으면 부를 곳이 없다 — AI 서버 없이 띄운 환경에서는 추천을 쓸 수 없다. */
    public boolean isConfigured() {
        return StringUtils.hasText(properties.baseUrl());
    }

    /**
     * 후보 추천을 요청한다. 응답 본문이 없거나 {@code candidates}가 없으면 계약 위반이라 {@link IllegalStateException}이다.
     * 후보를 하나도 못 고른 경우는 빈 배열로 와야 한다.
     */
    public AiRecommendResponse recommend(AiRecommendRequest request) {
        AiRecommendResponse response = restClient.post()
                .uri(properties.baseUrl() + RECOMMEND_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    if (StringUtils.hasText(properties.apiKey())) {
                        headers.setBearerAuth(properties.apiKey());
                    }
                })
                .body(request)
                .retrieve()
                .body(AiRecommendResponse.class);
        if (response == null || response.candidates() == null) {
            throw new IllegalStateException("AI 서버 팀원 추천 응답에 candidates가 없습니다.");
        }
        return response;
    }

    private static JdkClientHttpRequestFactory defaultRequestFactory() {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }
}

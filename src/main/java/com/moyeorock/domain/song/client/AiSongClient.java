package com.moyeorock.domain.song.client;

import com.moyeorock.domain.song.config.AiServerProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * AI 서버 곡 카탈로그 조회(이슈 #147). 백엔드가 페이지를 차례로 받아 SongService가 저장한다.
 * Service 안에 두면 테스트에서 AI 서버를 끊을 수 없어 별도 클래스로 둔다(KakaoOAuthClient와 같은 방식, 인터페이스 없음).
 *
 * <p>호출 실패(4xx·5xx·타임아웃)는 {@link org.springframework.web.client.RestClientException}을 그대로 던진다 —
 * 사용자 요청이 아니라 스케줄 작업이라 에러 응답으로 바꿀 곳이 없고, SongService가 로그를 남기고 그 회차를 끝낸다.
 */
@Component
public class AiSongClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);
    private static final String SONGS_PATH = "/v1/songs";

    private final AiServerProperties properties;
    private final RestClient restClient;

    @Autowired
    public AiSongClient(AiServerProperties properties) {
        this(properties, RestClient.builder().requestFactory(defaultRequestFactory()).build());
    }

    // 테스트용: MockRestServiceServer를 바인딩한 RestClient 주입
    AiSongClient(AiServerProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    /** base-url이 없으면 부를 곳이 없다 — 로컬처럼 AI 서버 없이 띄운 환경에서는 동기화를 건너뛴다. */
    public boolean isConfigured() {
        return StringUtils.hasText(properties.baseUrl());
    }

    /**
     * 카탈로그 한 페이지를 받는다. cursor가 null이면 첫 페이지다. 응답 본문이 없거나 {@code songs}가 없으면
     * 계약 위반이라 {@link IllegalStateException}이다.
     */
    public AiSongPage fetchSongs(String cursor, int limit) {
        AiSongPage page = restClient.get()
                .uri(songsUri(cursor, limit))
                .headers(headers -> {
                    if (StringUtils.hasText(properties.apiKey())) {
                        headers.setBearerAuth(properties.apiKey());
                    }
                })
                .retrieve()
                .body(AiSongPage.class);
        if (page == null || page.songs() == null) {
            throw new IllegalStateException("AI 서버 곡 목록 응답에 songs가 없습니다.");
        }
        return page;
    }

    // 커서는 AI 서버가 정하는 불투명한 값이라(base64 등) +·/·= 까지 모두 인코딩한다 — 변수로 넣고 encode()해야 엄격하게 인코딩된다
    private URI songsUri(String cursor, int limit) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.baseUrl())
                .path(SONGS_PATH)
                .queryParam("limit", "{limit}");
        Map<String, Object> variables = new HashMap<>();
        variables.put("limit", limit);
        if (cursor != null) {
            builder.queryParam("cursor", "{cursor}");
            variables.put("cursor", cursor);
        }
        return builder.encode().buildAndExpand(variables).toUri();
    }

    private static JdkClientHttpRequestFactory defaultRequestFactory() {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }
}

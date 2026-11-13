package com.moyeorock.domain.song.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 곡 추천 호출 설정(이슈 #171). AI 팀 안내대로 곡 동기화({@link AiServerProperties})와 따로 두지만, 값이 없으면
 * application.yml에서 {@code AI_SERVER_BASE_URL}·{@code AI_SERVER_API_KEY}를 그대로 쓴다(지금은 같은 주소·같은 키).
 * api-key는 {@code Authorization: Bearer}가 아니라 {@code X-AI-Recommend-Key} 헤더로 보낸다.
 * base-url에는 {@code /v1}을 붙이지 않는다.
 */
@ConfigurationProperties(prefix = "ai.recommend")
public record AiRecommendProperties(
        String baseUrl,
        String apiKey
) {
}

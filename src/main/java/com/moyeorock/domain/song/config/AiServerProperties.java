package com.moyeorock.domain.song.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 서버 호출 설정(이슈 #147). 백엔드가 AI 서버의 곡 카탈로그를 받아와 songs에 넣는다 — AI 서버는 백엔드를 부르지 않는다.
 * base-url이 비어 있으면 곡 동기화를 건너뛴다(AiSongClient#isConfigured). api-key는 {@code Authorization: Bearer}로 보낸다.
 */
@ConfigurationProperties(prefix = "ai.server")
public record AiServerProperties(
        String baseUrl,
        String apiKey
) {
}

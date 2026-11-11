package com.moyeorock.domain.practice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 연습 녹음 설정(practice.*). 값이 없어도 기본값으로 뜬다.
 *
 * @param audioUrlExpiryMinutes 상세 audioUrl(presigned GET)의 유효 시간. 한 번 듣는 동안(타임라인 이동마다
 *                              Range 요청이 나간다) 끊기지 않을 만큼 길고, 새어도 곧 만료될 만큼 짧게 둔다
 * @param ai                    AI 호출 설정
 */
@ConfigurationProperties(prefix = "practice")
public record PracticeProperties(
        @DefaultValue("30") int audioUrlExpiryMinutes,
        @DefaultValue Ai ai
) {

    /**
     * @param type               fake(기본값, 가짜 AI) | ai(실제 AI 서버 — AiPracticeAnalysisClient)
     * @param fakeDelaySeconds   가짜 AI가 결과를 주기 전에 기다리는 시간
     * @param baseUrl            실제 AI 서버 주소(AI_SERVER_BASE_URL). /v1은 붙이지 않는다. type=ai일 때만 필요하다
     * @param apiKey             실제 AI 서버 키(AI_SERVER_API_KEY, Bearer로 보낸다). type=ai일 때만 필요하다
     * @param highRecall         AI가 후보 구간을 더 많이 잡게 할지. 기본은 false(보수적으로 잡는다)
     * @param readTimeoutSeconds 실제 AI 응답 대기 상한. AI 팀 권장값 180초
     */
    public record Ai(
            @DefaultValue("fake") String type,
            @DefaultValue("5") int fakeDelaySeconds,
            String baseUrl,
            String apiKey,
            @DefaultValue("false") boolean highRecall,
            @DefaultValue("180") int readTimeoutSeconds
    ) {
    }
}

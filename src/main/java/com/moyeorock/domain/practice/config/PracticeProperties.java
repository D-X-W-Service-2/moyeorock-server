package com.moyeorock.domain.practice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 연습 녹음 설정(practice.*). 값이 없어도 기본값으로 뜬다.
 *
 * @param audioUrlExpiryMinutes 상세 audioUrl·AI 전달 URL(presigned GET)의 유효 시간. 한 번 듣는 동안(타임라인 이동마다
 *                              Range 요청이 나간다) 끊기지 않을 만큼 길고, 새어도 곧 만료될 만큼 짧게 둔다
 * @param ai                    AI 호출 설정
 */
@ConfigurationProperties(prefix = "practice")
public record PracticeProperties(
        @DefaultValue("30") int audioUrlExpiryMinutes,
        @DefaultValue Ai ai
) {

    /**
     * @param type             fake(기본값) — 실제 AI 구현이 들어오면 값이 늘어난다
     * @param fakeDelaySeconds 가짜 AI가 결과를 주기 전에 기다리는 시간
     */
    public record Ai(
            @DefaultValue("fake") String type,
            @DefaultValue("5") int fakeDelaySeconds
    ) {
    }
}

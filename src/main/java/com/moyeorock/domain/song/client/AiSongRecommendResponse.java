package com.moyeorock.domain.song.client;

import java.util.List;

/**
 * AI 곡 추천 응답(이슈 #171). {@code songs}는 점수 높은 순이고 최대 요청한 {@code limit}개다. {@code ranker}는 AI가 쓴 계산 방식
 * 이름(지금은 {@code content_rule})이라 응답에는 내보내지 않는다. {@code score}가 비어 있어도 읽을 수 있게 래퍼 타입을 쓴다.
 */
public record AiSongRecommendResponse(
        List<Item> songs,
        String ranker
) {

    public record Item(String externalId, Double score) {
    }
}

package com.moyeorock.domain.team.client;

import java.util.List;

/**
 * AI 서버 팀원 추천 응답(이슈 #150). 호출 형식은 AI 팀과 맞추기 전 잠정안이다(api-spec.md §12).
 * AI가 요청에 없던 userId를 돌려줘도 그대로 받는다 — 걸러내는 일은 TeamService가 한다.
 * {@code score}·{@code reasons}가 비어 있어도 읽을 수 있게 래퍼 타입을 쓴다.
 */
public record AiRecommendResponse(
        List<Item> candidates
) {

    public record Item(Long userId, Double score, List<String> reasons) {
    }
}

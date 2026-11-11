package com.moyeorock.domain.practice.client;

import java.util.List;

/**
 * AI 파트의 음성 분석 모델 호출. 우리 서버 → AI 한 방향이고 AI가 우리 API를 부르지 않는다.
 *
 * <p>구현체는 2개이고 {@code practice.ai.type}으로 고른다 — FileStorageClient(local·s3)와 같은 방식. 가짜 AI
 * {@link FakePracticeAnalysisClient}(fake, 기본값)와 실제 AI {@link AiPracticeAnalysisClient}(ai).
 */
public interface PracticeAnalysisClient {

    /**
     * 녹음 하나를 분석한다. 끝날 때까지 기다린다(첫 호출은 수십 초 걸린다). 실패는 RuntimeException으로 던지고,
     * 부르는 쪽이 녹음을 FAILED로 바꾼다 — API 응답이 아니라 실패 사유로 보여 주므로 BusinessException으로 바꾸지 않는다.
     * 사용자에게 보여 줄 사유가 정해진 실패는 {@link PracticeAnalysisException}이다.
     */
    List<AnalysisFeedback> analyze(AnalysisRequest request);
}

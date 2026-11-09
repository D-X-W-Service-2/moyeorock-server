package com.moyeorock.domain.practice.client;

import java.util.List;

/**
 * AI 파트의 음성 분석 모델 호출. 우리 서버 → AI 한 방향이고 AI가 우리 API를 부르지 않는다.
 *
 * <p>AI 서버가 아직 없고 응답 형식도 정해지지 않아(docs/plans/practice-recording.md "AI 팀 질문") 지금은 가짜 구현
 * {@link FakePracticeAnalysisClient}만 있다. 실제 구현(RestClient)이 들어오면 구현체가 2개가 되고
 * {@code practice.ai.type}으로 고른다 — FileStorageClient(local·s3)와 같은 방식. 그 전까지 인터페이스와 구현체가
 * 1:1인 것은 절대 규칙 3의 예외로 승인받았다(2026-11-09, 계획 D-5).
 */
public interface PracticeAnalysisClient {

    /**
     * 녹음 하나를 분석한다. 끝날 때까지 기다린다(수 분 걸릴 수 있다). 실패는 RuntimeException으로 던지고,
     * 부르는 쪽이 녹음을 FAILED로 바꾼다 — API 응답이 아니라 실패 사유로 보여 주므로 BusinessException으로 바꾸지 않는다.
     */
    List<AnalysisFeedback> analyze(AnalysisRequest request);
}

package com.moyeorock.domain.practice.client;

// AI 분석 결과 한 줄. 구간은 초 단위, part는 AI가 준 세션 문자열 그대로다 — 저장할 때 공용 Instrument로 바꾸고
// 바꿀 수 없는 값은 그 줄만 버린다(PracticeAnalysisService).
public record AnalysisFeedback(int startSec, int endSec, String part, String content) {
}

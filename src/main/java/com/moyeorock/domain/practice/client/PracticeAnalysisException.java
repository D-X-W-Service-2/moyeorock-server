package com.moyeorock.domain.practice.client;

/**
 * 사용자에게 보여 줄 사유가 정해진 분석 실패. 메시지가 그대로 녹음의 실패 사유(failure_reason, 255자)가 된다
 * (PracticeRecordingService.runAnalysis). 사유를 정할 수 없는 실패는 이 예외가 아니라 원래 예외를 그대로 던진다 — 그때는
 * 일반 사유("분석하지 못했습니다")가 된다.
 */
public class PracticeAnalysisException extends RuntimeException {

    public static final String UNSUPPORTED_SONG = "이 곡은 아직 연습 분석을 지원하지 않습니다.";
    public static final String REVIEW_REQUIRED = "녹음을 원곡과 맞춰 보지 못했습니다. 고른 곡을 연주한 녹음인지 확인해 주세요.";
    public static final String INVALID_RECORDING = "녹음을 분석할 수 없습니다. 3~60초 길이의 녹음인지 확인해 주세요.";
    public static final String AI_UNAVAILABLE = "AI 서버에 일시적인 문제가 있습니다. 잠시 후 다시 요청해 주세요.";

    public PracticeAnalysisException(String reason) {
        super(reason);
    }

    public PracticeAnalysisException(String reason, Throwable cause) {
        super(reason, cause);
    }
}

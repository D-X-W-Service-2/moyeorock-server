package com.moyeorock.domain.practice.client;

import com.moyeorock.global.common.enums.Instrument;

/**
 * AI에 넘기는 분석 요청 한 건. 원곡은 AI 서버가 갖고 있어 곡의 external_id만 넘기고, 연습 녹음은 파일 내용을 그대로 넘긴다
 * (실제 AI가 Base64로 실어 보낸다 — AI 팀 핸드오프 2026-11-11).
 *
 * @param songExternalId      연습한 곡의 external_id. AI가 이 값으로 원곡을 찾는다
 * @param performanceAudio    연습 녹음 파일 내용. 크기 한도는 FileService의 연습 녹음 한도(1,950,000바이트)
 * @param performanceFilename 확장자가 붙은 녹음 파일 이름 — AI가 확장자로 형식을 알아본다
 * @param part                녹음한 세션. AI 응답에 세션이 없어 피드백 줄의 세션으로 쓴다
 */
public record AnalysisRequest(String songExternalId, byte[] performanceAudio, String performanceFilename,
                              Instrument part) {
}

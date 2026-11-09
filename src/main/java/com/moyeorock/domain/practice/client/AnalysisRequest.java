package com.moyeorock.domain.practice.client;

// AI에 넘기는 분석 요청. audioUrl은 녹음 파일을 읽을 임시 URL(S3 presigned GET)이다.
// 파일 전달 방식(URL로 받아 가는지, 바이트를 올려야 하는지)은 AI 팀 확인 전이라 실제 구현 때 바뀔 수 있다.
public record AnalysisRequest(String audioUrl, String fileExtension) {
}

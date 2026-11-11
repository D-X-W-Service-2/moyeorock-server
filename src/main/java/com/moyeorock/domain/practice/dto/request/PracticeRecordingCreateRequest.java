package com.moyeorock.domain.practice.dto.request;

import com.moyeorock.global.common.enums.Instrument;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PracticeRecordingCreateRequest(
        // practice_recordings.title이 VARCHAR(100)이다(노션 "100자 이하").
        @NotBlank(message = "녹음 제목은 필수입니다.")
        @Size(max = 100, message = "녹음 제목은 100자 이하여야 합니다.")
        String title,

        // 녹음한 세션 — 혼자 연습한 세션 하나의 녹음이다(노션 2026-11-09 추가). AI에 세션 힌트로 넘긴다.
        @NotNull(message = "세션은 필수입니다.")
        Instrument part,

        // 연습한 곡(곡 검색 API의 id). AI가 이 곡의 원곡과 녹음을 맞춰 본다 — 원곡 음원은 AI 서버가 갖고 있다(AI 팀 핸드오프 2026-11-11).
        @NotNull(message = "곡은 필수입니다.")
        Long songId,

        // 파일 업로드 API(domain: PRACTICE_AUDIO)가 준 fileUrl. 그대로 저장하지 않고 "내가 올린 연습 녹음 파일"인지
        // 확인한 뒤 스토리지 key로 바꿔 저장한다(PracticeRecordingService.create) — 길이 상한은 그 확인이 대신한다.
        @NotBlank(message = "audioUrl은 필수입니다.")
        String audioUrl
) {
}

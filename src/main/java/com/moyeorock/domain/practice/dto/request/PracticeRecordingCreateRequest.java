package com.moyeorock.domain.practice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PracticeRecordingCreateRequest(
        // practice_recordings.title이 VARCHAR(100)이다(노션 "100자 이하").
        @NotBlank(message = "녹음 제목은 필수입니다.")
        @Size(max = 100, message = "녹음 제목은 100자 이하여야 합니다.")
        String title,

        // 파일 업로드 API(domain: PRACTICE_AUDIO)가 준 fileUrl. 그대로 저장하지 않고 "내가 올린 연습 녹음 파일"인지
        // 확인한 뒤 스토리지 key로 바꿔 저장한다(PracticeRecordingService.create) — 길이 상한은 그 확인이 대신한다.
        @NotBlank(message = "audioUrl은 필수입니다.")
        String audioUrl
) {
}

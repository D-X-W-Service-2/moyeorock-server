package com.moyeorock.domain.practice.dto.response;

import com.moyeorock.domain.practice.entity.PracticeRecording;
import com.moyeorock.domain.practice.enums.PracticeRecordingStatus;
import com.moyeorock.global.common.enums.Instrument;
import java.time.LocalDateTime;

// 분석 요청·다시 요청 응답과 내 연습 녹음 목록의 항목(노션 API 초안 "연습"). analyzedAt은 DONE일 때만 값이 있다.
public record PracticeRecordingSummaryResponse(
        Long id,
        String title,
        Instrument part,
        PracticeRecordingStatus status,
        LocalDateTime createdAt,
        LocalDateTime analyzedAt
) {

    public static PracticeRecordingSummaryResponse from(PracticeRecording recording) {
        return new PracticeRecordingSummaryResponse(recording.getId(), recording.getTitle(), recording.getPart(), recording.getStatus(),
                recording.getCreatedAt(), recording.getAnalyzedAt());
    }
}

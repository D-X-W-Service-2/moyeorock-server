package com.moyeorock.domain.practice.dto.response;

import com.moyeorock.domain.practice.entity.PracticeFeedback;
import com.moyeorock.domain.practice.entity.PracticeRecording;
import com.moyeorock.domain.practice.enums.PracticeRecordingStatus;
import com.moyeorock.global.common.enums.Instrument;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 연습 녹음 상세(분석 결과). audioUrl은 재생용 임시 URL이라 조회할 때마다 달라진다(녹음은 비공개, 유효 30분).
 * feedbacks는 DONE일 때만 채우고 구간 시작 순이다. failureReason은 FAILED일 때만 값이 있다.
 */
public record PracticeRecordingDetailResponse(
        Long id,
        String title,
        Instrument part,
        String audioUrl,
        PracticeRecordingStatus status,
        String failureReason,
        LocalDateTime createdAt,
        LocalDateTime analyzedAt,
        List<Feedback> feedbacks
) {

    public static PracticeRecordingDetailResponse of(PracticeRecording recording, String audioUrl,
            List<PracticeFeedback> feedbacks) {
        return new PracticeRecordingDetailResponse(recording.getId(), recording.getTitle(), recording.getPart(), audioUrl,
                recording.getStatus(), recording.getFailureReason(), recording.getCreatedAt(),
                recording.getAnalyzedAt(), feedbacks.stream().map(Feedback::from).toList());
    }

    // 분석 결과 한 줄 — 상세에서만 쓴다(dto-naming 규칙 6).
    public record Feedback(int startSec, int endSec, Instrument part, String content) {

        static Feedback from(PracticeFeedback feedback) {
            return new Feedback(feedback.getStartSec(), feedback.getEndSec(), feedback.getPart(),
                    feedback.getContent());
        }
    }
}

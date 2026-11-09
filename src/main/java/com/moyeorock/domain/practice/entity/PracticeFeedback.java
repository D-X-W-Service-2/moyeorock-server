package com.moyeorock.domain.practice.entity;

import com.moyeorock.global.common.enums.Instrument;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// erd.md §20. AI 분석 결과 한 줄. 분석이 끝날 때 한꺼번에 쓰고 고치지 않아 타임스탬프가 없다(architecture.md §5) —
// 생성 시각은 부모의 analyzed_at이다. 녹음은 recording_id(Long)로만 가리킨다(CLAUDE.md 절대 규칙 6).
@Entity
@Table(name = "practice_feedbacks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PracticeFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recording_id", nullable = false)
    private Long recordingId;

    @Column(name = "start_sec", nullable = false)
    private int startSec;

    @Column(name = "end_sec", nullable = false)
    private int endSec;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private Instrument part;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    public static PracticeFeedback create(Long recordingId, int startSec, int endSec, Instrument part, String content) {
        PracticeFeedback feedback = new PracticeFeedback();
        feedback.recordingId = recordingId;
        feedback.startSec = startSec;
        feedback.endSec = endSec;
        feedback.part = part;
        feedback.content = content;
        return feedback;
    }
}

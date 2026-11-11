package com.moyeorock.domain.practice.entity;

import com.moyeorock.domain.practice.enums.PracticeRecordingStatus;
import com.moyeorock.global.common.entity.BaseEntity;
import com.moyeorock.global.common.enums.Instrument;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// erd.md §19. 생성 컬럼 active_user_id(진행 중일 때만 user_id)는 DB가 계산하므로 매핑하지 않는다.
// 상태는 엔티티 메서드로만 바꾼다 — updated_at이 JPA 감사(JVM 시각)로 찍혀야 부팅 정리 기준과 맞는다
// (PracticeAnalysisService.failUnfinishedOnStartup).
@Entity
@Table(name = "practice_recordings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PracticeRecording extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String title;

    // 녹음한 세션. 혼자 연습한 세션 하나의 녹음이라 올릴 때 고른다(계획 D-10). AI에 세션 힌트로 넘긴다.
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private Instrument part;

    // 연습한 곡(songs.id). AI가 원곡을 이 곡의 external_id로 찾는다 — 원곡 파일은 AI 서버가 갖고 있다. 다른 테이블이라
    // Long으로만 가리킨다(CLAUDE.md 절대 규칙 6).
    @Column(name = "song_id", nullable = false)
    private Long songId;

    // URL이 아니라 스토리지 key다 — 녹음은 비공개라 읽을 때마다 임시 URL을 만든다(FileService.issueReadUrl).
    @Column(name = "audio_file_key", nullable = false)
    private String audioFileKey;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private PracticeRecordingStatus status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "analyzed_at")
    private LocalDateTime analyzedAt;

    public static PracticeRecording create(Long userId, String title, Instrument part, Long songId,
            String audioFileKey) {
        PracticeRecording recording = new PracticeRecording();
        recording.userId = userId;
        recording.title = title;
        recording.part = part;
        recording.songId = songId;
        recording.audioFileKey = audioFileKey;
        recording.status = PracticeRecordingStatus.SUBMITTED;
        return recording;
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    public boolean isSubmitted() {
        return status == PracticeRecordingStatus.SUBMITTED;
    }

    public boolean isAnalyzing() {
        return status == PracticeRecordingStatus.ANALYZING;
    }

    public boolean isInProgress() {
        return PracticeRecordingStatus.IN_PROGRESS.contains(status);
    }

    public boolean isDone() {
        return status == PracticeRecordingStatus.DONE;
    }

    public boolean isFailed() {
        return status == PracticeRecordingStatus.FAILED;
    }

    public void startAnalysis() {
        this.status = PracticeRecordingStatus.ANALYZING;
    }

    public void complete(LocalDateTime analyzedAt) {
        this.status = PracticeRecordingStatus.DONE;
        this.analyzedAt = analyzedAt;
    }

    public void fail(String reason) {
        this.status = PracticeRecordingStatus.FAILED;
        this.failureReason = reason;
    }

    // 실패한 녹음을 같은 파일로 다시 분석한다. 요청 시각(createdAt)은 처음 그대로 둔다.
    public void retry() {
        this.status = PracticeRecordingStatus.SUBMITTED;
        this.failureReason = null;
    }
}

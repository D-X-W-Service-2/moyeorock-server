package com.moyeorock.domain.practice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moyeorock.domain.practice.client.AnalysisFeedback;
import com.moyeorock.domain.practice.entity.PracticeFeedback;
import com.moyeorock.domain.practice.entity.PracticeRecording;
import com.moyeorock.domain.practice.enums.PracticeRecordingStatus;
import com.moyeorock.domain.practice.repository.PracticeFeedbackRepository;
import com.moyeorock.domain.practice.repository.PracticeRecordingRepository;
import com.moyeorock.global.common.enums.Instrument;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PracticeAnalysisServiceTest {

    private static final Long RECORDING_ID = 12L;

    @Mock
    private PracticeRecordingRepository recordingRepository;

    @Mock
    private PracticeFeedbackRepository feedbackRepository;

    private PracticeAnalysisService analysisService;

    @BeforeEach
    void setUp() {
        analysisService = new PracticeAnalysisService(recordingRepository, feedbackRepository);
    }

    @Test
    @DisplayName("대기 중인 녹음은 분석 중으로 바꾸고 그 녹음을 돌려준다(AI 요청에 파일 key·세션을 쓴다)")
    void start_movesSubmittedToAnalyzing() {
        PracticeRecording recording = PracticeRecording.create(1L, "연습", Instrument.VOCAL, "practice/a.m4a");
        when(recordingRepository.findByIdForUpdate(RECORDING_ID)).thenReturn(Optional.of(recording));

        assertThat(analysisService.start(RECORDING_ID)).containsSame(recording);
        assertThat(recording.getStatus()).isEqualTo(PracticeRecordingStatus.ANALYZING);
    }

    @Test
    @DisplayName("그새 지워졌거나 대기 중이 아니면(부팅 정리로 실패 처리 등) 분석하지 않는다")
    void start_returnsEmpty_whenDeletedOrNotSubmitted() {
        PracticeRecording failed = inStatus(PracticeRecordingStatus.FAILED);
        when(recordingRepository.findByIdForUpdate(RECORDING_ID)).thenReturn(Optional.empty());
        when(recordingRepository.findByIdForUpdate(13L)).thenReturn(Optional.of(failed));

        assertThat(analysisService.start(RECORDING_ID)).isEmpty();
        assertThat(analysisService.start(13L)).isEmpty();
        assertThat(failed.getStatus()).isEqualTo(PracticeRecordingStatus.FAILED);
    }

    @Test
    @DisplayName("분석 중인 녹음에 결과를 저장하고 분석 완료로 바꾼다")
    void complete_savesFeedbacks_andMarksDone() {
        PracticeRecording recording = inStatus(PracticeRecordingStatus.ANALYZING);
        when(recordingRepository.findByIdForUpdate(RECORDING_ID)).thenReturn(Optional.of(recording));

        boolean saved = analysisService.complete(RECORDING_ID, List.of(
                new AnalysisFeedback(80, 95, "VOCAL", "음정이 2도 플랫되었습니다"),
                new AnalysisFeedback(10, 12, "DRUM", "박자가 빠릅니다")));

        assertThat(saved).isTrue();
        assertThat(recording.getStatus()).isEqualTo(PracticeRecordingStatus.DONE);
        assertThat(recording.getAnalyzedAt()).isNotNull();
        assertThat(savedFeedbacks())
                .extracting(PracticeFeedback::getRecordingId, PracticeFeedback::getStartSec, PracticeFeedback::getEndSec,
                        PracticeFeedback::getPart, PracticeFeedback::getContent)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(RECORDING_ID, 80, 95, Instrument.VOCAL, "음정이 2도 플랫되었습니다"),
                        org.assertj.core.groups.Tuple.tuple(RECORDING_ID, 10, 12, Instrument.DRUM, "박자가 빠릅니다"));
    }

    @Test
    @DisplayName("세션 값을 알 수 없거나 구간·내용이 잘못된 줄은 그 줄만 버린다")
    void complete_skipsInvalidLines() {
        PracticeRecording recording = inStatus(PracticeRecordingStatus.ANALYZING);
        when(recordingRepository.findByIdForUpdate(RECORDING_ID)).thenReturn(Optional.of(recording));

        analysisService.complete(RECORDING_ID, List.of(
                new AnalysisFeedback(0, 5, "SAXOPHONE", "모르는 세션"),
                new AnalysisFeedback(0, 5, null, "세션 없음"),
                new AnalysisFeedback(-1, 5, "VOCAL", "음수 구간"),
                new AnalysisFeedback(10, 5, "VOCAL", "끝이 시작보다 앞"),
                new AnalysisFeedback(0, 5, "VOCAL", " "),
                new AnalysisFeedback(3, 3, "BASS", "정상")));

        assertThat(recording.getStatus()).isEqualTo(PracticeRecordingStatus.DONE);
        assertThat(savedFeedbacks()).extracting(PracticeFeedback::getContent).containsExactly("정상");
    }

    @Test
    @DisplayName("결과가 0건이면 빈 목록으로 분석 완료다")
    void complete_withNoResults_isDone() {
        PracticeRecording recording = inStatus(PracticeRecordingStatus.ANALYZING);
        when(recordingRepository.findByIdForUpdate(RECORDING_ID)).thenReturn(Optional.of(recording));

        assertThat(analysisService.complete(RECORDING_ID, List.of())).isTrue();
        assertThat(recording.getStatus()).isEqualTo(PracticeRecordingStatus.DONE);
        assertThat(savedFeedbacks()).isEmpty();
    }

    @Test
    @DisplayName("분석 중에 지워졌거나 분석 중이 아니면 결과를 버린다")
    void complete_discards_whenDeletedOrNotAnalyzing() {
        PracticeRecording failed = inStatus(PracticeRecordingStatus.FAILED);
        when(recordingRepository.findByIdForUpdate(RECORDING_ID)).thenReturn(Optional.empty());
        when(recordingRepository.findByIdForUpdate(13L)).thenReturn(Optional.of(failed));
        List<AnalysisFeedback> results = List.of(new AnalysisFeedback(0, 5, "VOCAL", "결과"));

        assertThat(analysisService.complete(RECORDING_ID, results)).isFalse();
        assertThat(analysisService.complete(13L, results)).isFalse();
        verify(feedbackRepository, never()).saveAll(anyList());
        assertThat(failed.getStatus()).isEqualTo(PracticeRecordingStatus.FAILED);
    }

    @Test
    @DisplayName("진행 중인 녹음은 실패와 사유로 바꾸고, 이미 끝났거나 지워졌으면 그대로 둔다")
    void fail_onlyInProgress() {
        PracticeRecording submitted = PracticeRecording.create(1L, "대기", Instrument.VOCAL, "practice/a.m4a");
        PracticeRecording done = inStatus(PracticeRecordingStatus.DONE);
        when(recordingRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(submitted));
        when(recordingRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(done));
        when(recordingRepository.findByIdForUpdate(3L)).thenReturn(Optional.empty());

        analysisService.fail(1L, "사유");
        analysisService.fail(2L, "사유");
        analysisService.fail(3L, "사유");

        assertThat(submitted).extracting(PracticeRecording::getStatus, PracticeRecording::getFailureReason)
                .containsExactly(PracticeRecordingStatus.FAILED, "사유");
        assertThat(done.getStatus()).isEqualTo(PracticeRecordingStatus.DONE);
        assertThat(done.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("부팅 정리는 빈이 만들어진 시각을 기준으로 진행 중 녹음을 재시작 사유와 함께 실패로 바꾼다")
    void failUnfinishedOnStartup_usesBootTime() {
        LocalDateTime afterBoot = LocalDateTime.now();

        analysisService.failUnfinishedOnStartup();

        ArgumentCaptor<LocalDateTime> bootedAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(recordingRepository).failUnfinishedBefore(bootedAt.capture(),
                eq(PracticeAnalysisService.REASON_RESTARTED), any(LocalDateTime.class),
                eq(PracticeRecordingStatus.FAILED), eq(PracticeRecordingStatus.IN_PROGRESS));
        assertThat(bootedAt.getValue()).isBeforeOrEqualTo(afterBoot);
    }

    @SuppressWarnings("unchecked")
    private List<PracticeFeedback> savedFeedbacks() {
        ArgumentCaptor<List<PracticeFeedback>> captor = ArgumentCaptor.forClass(List.class);
        verify(feedbackRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private static PracticeRecording inStatus(PracticeRecordingStatus status) {
        PracticeRecording recording = PracticeRecording.create(1L, "연습", Instrument.VOCAL, "practice/a.m4a");
        if (status == PracticeRecordingStatus.SUBMITTED) {
            return recording;
        }
        recording.startAnalysis();
        if (status == PracticeRecordingStatus.DONE) {
            recording.complete(LocalDateTime.now());
        } else if (status == PracticeRecordingStatus.FAILED) {
            recording.fail("이전 실패");
        }
        return recording;
    }
}

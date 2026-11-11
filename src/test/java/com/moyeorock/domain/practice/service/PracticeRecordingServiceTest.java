package com.moyeorock.domain.practice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moyeorock.domain.practice.client.AnalysisFeedback;
import com.moyeorock.domain.practice.client.AnalysisRequest;
import com.moyeorock.domain.practice.client.PracticeAnalysisClient;
import com.moyeorock.domain.practice.client.PracticeAnalysisException;
import com.moyeorock.domain.practice.config.PracticeProperties;
import com.moyeorock.domain.practice.dto.request.PracticeRecordingCreateRequest;
import com.moyeorock.domain.practice.dto.response.PracticeRecordingDetailResponse;
import com.moyeorock.domain.practice.dto.response.PracticeRecordingSummaryResponse;
import com.moyeorock.domain.practice.entity.PracticeFeedback;
import com.moyeorock.domain.practice.entity.PracticeRecording;
import com.moyeorock.domain.practice.enums.PracticeRecordingStatus;
import com.moyeorock.domain.practice.repository.PracticeFeedbackRepository;
import com.moyeorock.domain.practice.repository.PracticeRecordingRepository;
import com.moyeorock.domain.song.dto.response.SongSummaryResponse;
import com.moyeorock.domain.song.service.SongService;
import com.moyeorock.global.common.entity.BaseTimeEntity;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import com.moyeorock.global.file.enums.UploadDomain;
import com.moyeorock.global.file.service.FileService;
import java.lang.reflect.Field;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.ResourceAccessException;

/**
 * 분석은 커밋 뒤에 실행기로 넘어간다. 실제 트랜잭션 없이 그 시점을 보려고 트랜잭션 동기화만 직접 켜고
 * ({@link TransactionSynchronizationManager#initSynchronization()}) 커밋 후 콜백을 손으로 부른다. 실행기는
 * {@link SyncTaskExecutor}라 넘긴 분석이 같은 스레드에서 바로 돈다.
 */
@ExtendWith(MockitoExtension.class)
class PracticeRecordingServiceTest {

    private static final Long ME = 1L;
    private static final Long OTHER = 2L;
    private static final Long RECORDING_ID = 12L;
    private static final String AUDIO_URL = "https://files/practice/key.m4a";
    private static final String FILE_KEY = "practice/key.m4a";
    private static final String READ_URL = "https://signed/practice/key.m4a?X-Amz-Signature=x";
    private static final long MAX_BYTES = 1_950_000L;
    private static final Long SONG_ID = 7L;
    private static final String SONG_EXTERNAL_ID = "local-ce9924cfa2b2a7d2d4a37f46";
    private static final byte[] AUDIO = {1, 2, 3};
    private static final Duration URL_EXPIRY = Duration.ofMinutes(30);
    private static final LocalDateTime REQUESTED_AT = LocalDateTime.of(2026, 11, 9, 14, 30);

    @Mock
    private PracticeRecordingRepository recordingRepository;
    @Mock
    private PracticeFeedbackRepository feedbackRepository;
    @Mock
    private PracticeAnalysisService analysisService;
    @Mock
    private PracticeAnalysisClient analysisClient;
    @Mock
    private FileService fileService;
    @Mock
    private SongService songService;

    private PracticeRecordingService service;

    @BeforeEach
    void setUp() {
        service = serviceWith(new SyncTaskExecutor());
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // ---- 분석 요청

    @Test
    @DisplayName("분석 요청은 SUBMITTED로 저장하고, 분석은 커밋된 뒤에야 넘긴다")
    void create_savesSubmitted_andStartsAnalysisOnlyAfterCommit() {
        stubUploadedAudio(1024);
        stubSongExists();
        when(recordingRepository.existsByUserIdAndStatusIn(ME, PracticeRecordingStatus.IN_PROGRESS)).thenReturn(false);
        when(recordingRepository.saveAndFlush(any())).thenAnswer(invocation -> saved(invocation.getArgument(0)));
        when(analysisService.start(RECORDING_ID)).thenReturn(Optional.empty());
        TransactionSynchronizationManager.initSynchronization();

        PracticeRecordingSummaryResponse response = service.create(ME, request("11/9 보컬 연습"));

        assertThat(response).extracting(PracticeRecordingSummaryResponse::id, PracticeRecordingSummaryResponse::status,
                        PracticeRecordingSummaryResponse::createdAt, PracticeRecordingSummaryResponse::analyzedAt)
                .containsExactly(RECORDING_ID, PracticeRecordingStatus.SUBMITTED, REQUESTED_AT, null);
        assertThat(response.part()).isEqualTo(Instrument.VOCAL);
        ArgumentCaptor<PracticeRecording> captor = ArgumentCaptor.forClass(PracticeRecording.class);
        verify(recordingRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue()).extracting(PracticeRecording::getUserId, PracticeRecording::getTitle,
                        PracticeRecording::getPart, PracticeRecording::getSongId, PracticeRecording::getAudioFileKey)
                .containsExactly(ME, "11/9 보컬 연습", Instrument.VOCAL, SONG_ID, FILE_KEY);

        verify(analysisService, never()).start(anyLong());
        commit();
        verify(analysisService).start(RECORDING_ID);
    }

    @Test
    @DisplayName("내가 올린 연습 녹음 파일이 아니면 400 VALIDATION_FAILED, 저장하지 않는다")
    void create_rejectsAudioUrlNotMine() {
        when(fileService.findOwnedFileKey(AUDIO_URL, UploadDomain.PRACTICE_AUDIO, ME)).thenReturn(Optional.empty());

        assertValidationFailed(() -> service.create(ME, request("연습")),
                "audioUrl: 내가 올린 연습 녹음 파일이 아닙니다.");
        verify(recordingRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("업로드가 끝나지 않은 파일이면 400 VALIDATION_FAILED")
    void create_rejectsNotUploadedFile() {
        when(fileService.findOwnedFileKey(AUDIO_URL, UploadDomain.PRACTICE_AUDIO, ME)).thenReturn(Optional.of(FILE_KEY));
        when(fileService.findSize(FILE_KEY)).thenReturn(OptionalLong.empty());

        assertValidationFailed(() -> service.create(ME, request("연습")),
                "audioUrl: 업로드가 끝나지 않은 파일입니다.");
        verify(recordingRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("1.95MB를 넘는 파일이면 400 VALIDATION_FAILED — S3는 업로드 때 크기를 막지 못해 여기서 본다")
    void create_rejectsFileOverLimit() {
        stubUploadedAudio(MAX_BYTES + 1);

        assertValidationFailed(() -> service.create(ME, request("연습")), "audioUrl: 1.95MB를 넘는 파일입니다.");
        verify(recordingRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("없는 곡이면 404 SONG_NOT_FOUND, 저장하지 않는다")
    void create_rejectsUnknownSong() {
        stubUploadedAudio(MAX_BYTES);
        when(songService.getSummaries(List.of(SONG_ID))).thenReturn(Map.of());

        assertErrorCode(() -> service.create(ME, request("연습")), ErrorCode.SONG_NOT_FOUND);
        verify(recordingRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("진행 중인 내 녹음이 있으면 409 PRACTICE_ANALYSIS_IN_PROGRESS, 저장하지 않는다")
    void create_conflicts_whenAnalysisInProgress() {
        stubUploadedAudio(MAX_BYTES);
        stubSongExists();
        when(recordingRepository.existsByUserIdAndStatusIn(ME, PracticeRecordingStatus.IN_PROGRESS)).thenReturn(true);

        assertErrorCode(() -> service.create(ME, request("연습")),
                ErrorCode.PRACTICE_ANALYSIS_IN_PROGRESS);
        verify(recordingRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("동시 요청이 겹쳐 진행 중 유니크에 걸리면 500이 아니라 409다")
    void create_turnsActiveUserUniqueViolationInto409() {
        stubUploadedAudio(1024);
        stubSongExists();
        when(recordingRepository.existsByUserIdAndStatusIn(ME, PracticeRecordingStatus.IN_PROGRESS)).thenReturn(false);
        when(recordingRepository.saveAndFlush(any())).thenThrow(uniqueViolation(
                "Duplicate entry '1' for key 'practice_recordings.uk_practice_recordings_active_user_id'"));

        assertErrorCode(() -> service.create(ME, request("연습")),
                ErrorCode.PRACTICE_ANALYSIS_IN_PROGRESS);
    }

    @Test
    @DisplayName("다른 제약 위반은 409로 바꾸지 않고 그대로 던진다")
    void create_rethrowsOtherIntegrityViolation() {
        stubUploadedAudio(1024);
        stubSongExists();
        when(recordingRepository.existsByUserIdAndStatusIn(ME, PracticeRecordingStatus.IN_PROGRESS)).thenReturn(false);
        when(recordingRepository.saveAndFlush(any())).thenThrow(uniqueViolation("Data too long for column 'title'"));

        assertThatThrownBy(() -> service.create(ME, request("연습")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---- 상세

    @Test
    @DisplayName("남의 녹음은 없는 녹음과 같게 404이고 임시 URL을 만들지 않는다")
    void getDetail_returns404_forOthersRecording() {
        when(recordingRepository.findById(RECORDING_ID)).thenReturn(Optional.of(recordingOf(OTHER, PracticeRecordingStatus.DONE)));

        assertErrorCode(() -> service.getDetail(ME, RECORDING_ID), ErrorCode.PRACTICE_RECORDING_NOT_FOUND);
        verify(fileService, never()).issueReadUrl(any(), any());
    }

    @Test
    @DisplayName("분석 완료면 피드백을 구간 순으로, audioUrl은 30분짜리 임시 URL로 준다")
    void getDetail_done_includesFeedbacksAndReadUrl() {
        when(recordingRepository.findById(RECORDING_ID)).thenReturn(Optional.of(recordingOf(ME, PracticeRecordingStatus.DONE)));
        when(feedbackRepository.findByRecordingIdOrderByStartSecAscIdAsc(RECORDING_ID)).thenReturn(List.of(
                PracticeFeedback.create(RECORDING_ID, 80, 95, Instrument.VOCAL, "음정이 2도 플랫되었습니다")));
        when(fileService.issueReadUrl(FILE_KEY, URL_EXPIRY)).thenReturn(READ_URL);

        PracticeRecordingDetailResponse response = service.getDetail(ME, RECORDING_ID);

        assertThat(response.audioUrl()).isEqualTo(READ_URL);
        assertThat(response.part()).isEqualTo(Instrument.VOCAL);
        assertThat(response.status()).isEqualTo(PracticeRecordingStatus.DONE);
        assertThat(response.analyzedAt()).isNotNull();
        assertThat(response.feedbacks()).containsExactly(
                new PracticeRecordingDetailResponse.Feedback(80, 95, Instrument.VOCAL, "음정이 2도 플랫되었습니다"));
    }

    @Test
    @DisplayName("분석 완료가 아니면 피드백을 조회하지 않고 빈 목록, 실패면 사유를 준다")
    void getDetail_notDone_hasNoFeedbacks() {
        when(recordingRepository.findById(RECORDING_ID)).thenReturn(Optional.of(recordingOf(ME, PracticeRecordingStatus.FAILED)));
        when(fileService.issueReadUrl(FILE_KEY, URL_EXPIRY)).thenReturn(READ_URL);

        PracticeRecordingDetailResponse response = service.getDetail(ME, RECORDING_ID);

        assertThat(response.feedbacks()).isEmpty();
        assertThat(response.failureReason()).isEqualTo("이전 실패");
        assertThat(response.analyzedAt()).isNull();
        verify(feedbackRepository, never()).findByRecordingIdOrderByStartSecAscIdAsc(any());
    }

    // ---- 다시 요청

    @Test
    @DisplayName("남의 녹음을 다시 요청하면 404")
    void retry_returns404_forOthersRecording() {
        when(recordingRepository.findByIdForUpdate(RECORDING_ID))
                .thenReturn(Optional.of(recordingOf(OTHER, PracticeRecordingStatus.FAILED)));

        assertErrorCode(() -> service.retry(ME, RECORDING_ID), ErrorCode.PRACTICE_RECORDING_NOT_FOUND);
    }

    @Test
    @DisplayName("실패한 녹음이 아니면 409 INVALID_STATE")
    void retry_invalidState_whenNotFailed() {
        when(recordingRepository.findByIdForUpdate(RECORDING_ID))
                .thenReturn(Optional.of(recordingOf(ME, PracticeRecordingStatus.SUBMITTED)));
        when(recordingRepository.findByIdForUpdate(13L)).thenReturn(Optional.of(recordingOf(ME, PracticeRecordingStatus.DONE)));

        assertErrorCode(() -> service.retry(ME, RECORDING_ID), ErrorCode.INVALID_STATE);
        assertErrorCode(() -> service.retry(ME, 13L), ErrorCode.INVALID_STATE);
    }

    @Test
    @DisplayName("진행 중인 다른 내 녹음이 있으면 409 PRACTICE_ANALYSIS_IN_PROGRESS, 상태를 바꾸지 않는다")
    void retry_conflicts_whenAnotherInProgress() {
        PracticeRecording failed = recordingOf(ME, PracticeRecordingStatus.FAILED);
        when(recordingRepository.findByIdForUpdate(RECORDING_ID)).thenReturn(Optional.of(failed));
        when(recordingRepository.existsByUserIdAndStatusIn(ME, PracticeRecordingStatus.IN_PROGRESS)).thenReturn(true);

        assertErrorCode(() -> service.retry(ME, RECORDING_ID), ErrorCode.PRACTICE_ANALYSIS_IN_PROGRESS);
        assertThat(failed.getStatus()).isEqualTo(PracticeRecordingStatus.FAILED);
    }

    @Test
    @DisplayName("실패한 녹음은 SUBMITTED로 되돌리고 사유를 비우며 요청 시각은 그대로, 커밋 뒤 다시 분석한다")
    void retry_resubmitsFailedRecording_afterCommit() {
        PracticeRecording failed = recordingOf(ME, PracticeRecordingStatus.FAILED);
        when(recordingRepository.findByIdForUpdate(RECORDING_ID)).thenReturn(Optional.of(failed));
        when(recordingRepository.existsByUserIdAndStatusIn(ME, PracticeRecordingStatus.IN_PROGRESS)).thenReturn(false);
        when(analysisService.start(RECORDING_ID)).thenReturn(Optional.empty());
        TransactionSynchronizationManager.initSynchronization();

        PracticeRecordingSummaryResponse response = service.retry(ME, RECORDING_ID);

        assertThat(response.status()).isEqualTo(PracticeRecordingStatus.SUBMITTED);
        assertThat(response.createdAt()).isEqualTo(REQUESTED_AT);
        assertThat(failed.getFailureReason()).isNull();
        verify(recordingRepository).flush();
        verify(analysisService, never()).start(anyLong());
        commit();
        verify(analysisService).start(RECORDING_ID);
    }

    @Test
    @DisplayName("분석 대기열이 가득 차면 접수는 되지만 그 녹음을 바로 실패(사유: 요청이 많음)로 바꾼다")
    void retry_failsRecording_whenQueueIsFull() {
        TaskExecutor full = task -> {
            throw new TaskRejectedException("대기열 가득 참");
        };
        service = serviceWith(full);
        when(recordingRepository.findByIdForUpdate(RECORDING_ID))
                .thenReturn(Optional.of(recordingOf(ME, PracticeRecordingStatus.FAILED)));
        when(recordingRepository.existsByUserIdAndStatusIn(ME, PracticeRecordingStatus.IN_PROGRESS)).thenReturn(false);
        TransactionSynchronizationManager.initSynchronization();

        service.retry(ME, RECORDING_ID);
        commit();

        verify(analysisService).fail(RECORDING_ID, PracticeRecordingService.REASON_BUSY);
        verify(analysisService, never()).start(anyLong());
    }

    // ---- 삭제

    @Test
    @DisplayName("남의 녹음을 지우려 하면 404이고 아무것도 지우지 않는다")
    void delete_returns404_forOthersRecording() {
        when(recordingRepository.findByIdForUpdate(RECORDING_ID))
                .thenReturn(Optional.of(recordingOf(OTHER, PracticeRecordingStatus.DONE)));

        assertErrorCode(() -> service.delete(ME, RECORDING_ID), ErrorCode.PRACTICE_RECORDING_NOT_FOUND);
        verify(feedbackRepository, never()).deleteByRecordingId(any());
        verify(recordingRepository, never()).delete(any());
    }

    @Test
    @DisplayName("분석 중이어도 지울 수 있고, 피드백 → 녹음 순으로 지운다")
    void delete_removesFeedbacksThenRecording() {
        PracticeRecording analyzing = recordingOf(ME, PracticeRecordingStatus.ANALYZING);
        when(recordingRepository.findByIdForUpdate(RECORDING_ID)).thenReturn(Optional.of(analyzing));

        service.delete(ME, RECORDING_ID);

        InOrder order = inOrder(feedbackRepository, recordingRepository);
        order.verify(feedbackRepository).deleteByRecordingId(RECORDING_ID);
        order.verify(recordingRepository).delete(analyzing);
    }

    // ---- 회원 탈퇴 (1팀 UserWithdrawService가 부른다)

    @Test
    @DisplayName("탈퇴: 녹음이 없으면 아무것도 지우지 않는다")
    void deleteAllOf_doesNothing_whenNoRecordings() {
        when(recordingRepository.findAllByUserIdForUpdate(ME)).thenReturn(List.of());

        service.deleteAllOf(ME);

        verify(feedbackRepository, never()).deleteByRecordingIdIn(any());
        verify(recordingRepository, never()).deleteAllInBatch(any());
        verify(fileService, never()).deleteOwnedFiles(any(), any());
    }

    @Test
    @DisplayName("탈퇴: 분석 중인 것까지 내 녹음 전부를 잠금 조회로 잡고 피드백 → 녹음 → 녹음 파일 순으로 지운다")
    void deleteAllOf_removesFeedbacksRecordingsAndFiles() {
        PracticeRecording done = recordingOf(ME, PracticeRecordingStatus.DONE);
        PracticeRecording analyzing = withIdAndKey(recordingOf(ME, PracticeRecordingStatus.ANALYZING), 13L, "practice/other.m4a");
        List<PracticeRecording> mine = List.of(done, analyzing);
        when(recordingRepository.findAllByUserIdForUpdate(ME)).thenReturn(mine);

        service.deleteAllOf(ME);

        InOrder order = inOrder(feedbackRepository, recordingRepository, fileService);
        order.verify(feedbackRepository).deleteByRecordingIdIn(List.of(RECORDING_ID, 13L));
        order.verify(recordingRepository).deleteAllInBatch(mine);
        order.verify(fileService).deleteOwnedFiles(ME, List.of(FILE_KEY, "practice/other.m4a"));
    }

    // ---- 분석 실행(실행기 스레드)

    @Test
    @DisplayName("분석: 시작 → 곡 external_id·녹음 파일 내용·파일 이름·세션으로 AI 호출 → 결과 저장")
    void runAnalysis_callsAiWithExternalIdAndAudio_andSavesResult() {
        List<AnalysisFeedback> results = List.of(new AnalysisFeedback(0, 5, "VOCAL", "결과"));
        when(analysisService.start(RECORDING_ID)).thenReturn(Optional.of(recordingOf(ME, PracticeRecordingStatus.ANALYZING)));
        stubAnalysisInput();
        when(analysisClient.analyze(any())).thenReturn(results);

        service.runAnalysis(RECORDING_ID);

        ArgumentCaptor<AnalysisRequest> captor = ArgumentCaptor.forClass(AnalysisRequest.class);
        verify(analysisClient).analyze(captor.capture());
        assertThat(captor.getValue()).extracting(AnalysisRequest::songExternalId, AnalysisRequest::performanceAudio,
                        AnalysisRequest::performanceFilename, AnalysisRequest::part)
                .containsExactly(SONG_EXTERNAL_ID, AUDIO, "key.m4a", Instrument.VOCAL);
        verify(analysisService).complete(RECORDING_ID, results);
        verify(analysisService, never()).fail(anyLong(), any());
    }

    @Test
    @DisplayName("곡에 external_id가 없으면 AI를 부르지 않고 실패(사유: 지원하지 않는 곡)로 바꾼다")
    void runAnalysis_failsWithUnsupportedSong_whenNoExternalId() {
        when(analysisService.start(RECORDING_ID)).thenReturn(Optional.of(recordingOf(ME, PracticeRecordingStatus.ANALYZING)));
        when(songService.findExternalId(SONG_ID)).thenReturn(Optional.empty());

        service.runAnalysis(RECORDING_ID);

        verify(analysisClient, never()).analyze(any());
        verify(analysisService).fail(RECORDING_ID, PracticeAnalysisException.UNSUPPORTED_SONG);
    }

    @Test
    @DisplayName("AI가 사유를 정해 준 실패(원곡과 맞추지 못함 등)는 그 사유로 실패시킨다")
    void runAnalysis_failsWithGivenReason_onPracticeAnalysisException() {
        when(analysisService.start(RECORDING_ID)).thenReturn(Optional.of(recordingOf(ME, PracticeRecordingStatus.ANALYZING)));
        stubAnalysisInput();
        when(analysisClient.analyze(any()))
                .thenThrow(new PracticeAnalysisException(PracticeAnalysisException.REVIEW_REQUIRED));

        service.runAnalysis(RECORDING_ID);

        verify(analysisService).fail(RECORDING_ID, PracticeAnalysisException.REVIEW_REQUIRED);
        verify(analysisService, never()).complete(anyLong(), any());
    }

    @Test
    @DisplayName("분석 시작 전에 지워졌거나 이미 처리됐으면 AI를 부르지 않는다")
    void runAnalysis_skips_whenNotStartable() {
        when(analysisService.start(RECORDING_ID)).thenReturn(Optional.empty());

        service.runAnalysis(RECORDING_ID);

        verify(analysisClient, never()).analyze(any());
        verify(analysisService, never()).fail(anyLong(), any());
    }

    @Test
    @DisplayName("AI 연결 실패·응답 대기 초과면 실패(사유: AI 서버 연결)로 바꾼다")
    void runAnalysis_failsWithUnreachableReason_onConnectionError() {
        when(analysisService.start(RECORDING_ID)).thenReturn(Optional.of(recordingOf(ME, PracticeRecordingStatus.ANALYZING)));
        stubAnalysisInput();
        when(analysisClient.analyze(any())).thenThrow(new ResourceAccessException("Read timed out"));

        service.runAnalysis(RECORDING_ID);

        verify(analysisService).fail(RECORDING_ID, PracticeRecordingService.REASON_AI_UNREACHABLE);
        verify(analysisService, never()).complete(anyLong(), any());
    }

    @Test
    @DisplayName("AI 오류 응답 등 그 밖의 실패는 실패(사유: 분석하지 못함)로 바꾼다")
    void runAnalysis_failsWithGenericReason_onOtherError() {
        when(analysisService.start(RECORDING_ID)).thenReturn(Optional.of(recordingOf(ME, PracticeRecordingStatus.ANALYZING)));
        stubAnalysisInput();
        when(analysisClient.analyze(any())).thenThrow(new IllegalStateException("AI 500"));

        service.runAnalysis(RECORDING_ID);

        verify(analysisService).fail(RECORDING_ID, PracticeRecordingService.REASON_ANALYSIS_FAILED);
    }

    @Test
    @DisplayName("분석 시작 자체가 실패하면(DB 오류 등) AI를 부르지 않고 실패로 바꾼다")
    void runAnalysis_failsWithoutAi_whenStartThrows() {
        when(analysisService.start(RECORDING_ID)).thenThrow(new IllegalStateException("DB 오류"));

        service.runAnalysis(RECORDING_ID);

        verify(analysisClient, never()).analyze(any());
        verify(analysisService).fail(RECORDING_ID, PracticeRecordingService.REASON_ANALYSIS_FAILED);
    }

    // ---- 도우미

    private PracticeRecordingService serviceWith(TaskExecutor executor) {
        return new PracticeRecordingService(recordingRepository, feedbackRepository, analysisService, analysisClient,
                fileService, songService, new PracticeProperties(30, new PracticeProperties.Ai("fake", 0, null, null, false, 180)),
                executor);
    }

    private static PracticeRecordingCreateRequest request(String title) {
        return new PracticeRecordingCreateRequest(title, Instrument.VOCAL, SONG_ID, AUDIO_URL);
    }

    private void stubSongExists() {
        when(songService.getSummaries(List.of(SONG_ID)))
                .thenReturn(Map.of(SONG_ID, new SongSummaryResponse(SONG_ID, "예뻤어", "DAY6")));
    }

    private void stubAnalysisInput() {
        when(songService.findExternalId(SONG_ID)).thenReturn(Optional.of(SONG_EXTERNAL_ID));
        when(fileService.maxUploadBytes(UploadDomain.PRACTICE_AUDIO)).thenReturn(MAX_BYTES);
        when(fileService.readBytes(FILE_KEY, MAX_BYTES)).thenReturn(AUDIO);
    }

    private void stubUploadedAudio(long size) {
        when(fileService.findOwnedFileKey(AUDIO_URL, UploadDomain.PRACTICE_AUDIO, ME)).thenReturn(Optional.of(FILE_KEY));
        when(fileService.findSize(FILE_KEY)).thenReturn(OptionalLong.of(size));
        when(fileService.maxUploadBytes(UploadDomain.PRACTICE_AUDIO)).thenReturn(MAX_BYTES);
    }

    // 실제 트랜잭션이 커밋될 때처럼 등록된 커밋 후 콜백을 부른다.
    private static void commit() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }

    private static DataIntegrityViolationException uniqueViolation(String sqlMessage) {
        return new DataIntegrityViolationException("could not execute statement",
                new SQLIntegrityConstraintViolationException(sqlMessage));
    }

    private static void assertValidationFailed(org.assertj.core.api.ThrowableAssert.ThrowingCallable call,
            String message) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .hasMessage(message)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    private static void assertErrorCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(code);
    }

    private static PracticeRecording recordingOf(Long userId, PracticeRecordingStatus status) {
        PracticeRecording recording = saved(PracticeRecording.create(userId, "연습", Instrument.VOCAL, SONG_ID, FILE_KEY));
        if (status == PracticeRecordingStatus.SUBMITTED) {
            return recording;
        }
        recording.startAnalysis();
        if (status == PracticeRecordingStatus.DONE) {
            recording.complete(REQUESTED_AT.plusMinutes(3));
        } else if (status == PracticeRecordingStatus.FAILED) {
            recording.fail("이전 실패");
        }
        return recording;
    }

    private static PracticeRecording withIdAndKey(PracticeRecording recording, Long id, String fileKey) {
        try {
            Field idField = PracticeRecording.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(recording, id);
            Field keyField = PracticeRecording.class.getDeclaredField("audioFileKey");
            keyField.setAccessible(true);
            keyField.set(recording, fileKey);
            return recording;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    // id(GeneratedValue)와 createdAt(JPA 감사)은 저장할 때 채워진다 — 리플렉션으로 저장된 것처럼 흉내 낸다.
    private static PracticeRecording saved(PracticeRecording recording) {
        try {
            Field id = PracticeRecording.class.getDeclaredField("id");
            id.setAccessible(true);
            id.set(recording, RECORDING_ID);
            Field createdAt = BaseTimeEntity.class.getDeclaredField("createdAt");
            createdAt.setAccessible(true);
            createdAt.set(recording, REQUESTED_AT);
            return recording;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}

package com.moyeorock.domain.practice.service;

import com.moyeorock.domain.practice.client.AnalysisFeedback;
import com.moyeorock.domain.practice.client.AnalysisRequest;
import com.moyeorock.domain.practice.client.PracticeAnalysisClient;
import com.moyeorock.domain.practice.config.PracticeAnalysisConfig;
import com.moyeorock.domain.practice.config.PracticeProperties;
import com.moyeorock.domain.practice.dto.request.PracticeRecordingCreateRequest;
import com.moyeorock.domain.practice.dto.response.PracticeRecordingDetailResponse;
import com.moyeorock.domain.practice.dto.response.PracticeRecordingSummaryResponse;
import com.moyeorock.domain.practice.entity.PracticeFeedback;
import com.moyeorock.domain.practice.entity.PracticeRecording;
import com.moyeorock.domain.practice.enums.PracticeRecordingStatus;
import com.moyeorock.domain.practice.repository.PracticeFeedbackRepository;
import com.moyeorock.domain.practice.repository.PracticeRecordingRepository;
import com.moyeorock.global.common.dto.PageResponse;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import com.moyeorock.global.file.enums.UploadDomain;
import com.moyeorock.global.file.service.FileService;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.ResourceAccessException;

/**
 * 개인 연습 녹음 API 5개와 AI 분석 실행(이슈 #155). 녹음과 결과는 올린 본인만 본다 — 남의 녹음은 없는 녹음과 같게 404.
 *
 * <p>분석은 비동기다. 요청 트랜잭션이 커밋된 뒤 전용 단일 스레드 실행기({@link PracticeAnalysisConfig})에 넘기고 바로 202를
 * 돌려준다. 커밋 전에 넘기면 실행기 스레드가 아직 커밋 안 된 행을 못 찾는다.
 */
@Slf4j
@Service
public class PracticeRecordingService {

    private static final Sort LATEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id");
    // V13__create_practice_tables.sql — 진행 중 분석 사람당 1개
    private static final String ACTIVE_USER_UNIQUE_INDEX = "uk_practice_recordings_active_user_id";

    static final String REASON_BUSY = "요청이 많아 분석을 시작하지 못했습니다. 잠시 후 다시 요청해 주세요.";
    static final String REASON_AI_UNREACHABLE = "AI 서버에 연결하지 못했습니다. 잠시 후 다시 요청해 주세요.";
    static final String REASON_ANALYSIS_FAILED = "녹음을 분석하지 못했습니다.";

    private final PracticeRecordingRepository recordingRepository;
    private final PracticeFeedbackRepository feedbackRepository;
    private final PracticeAnalysisService analysisService;
    private final PracticeAnalysisClient analysisClient;
    private final FileService fileService;
    private final PracticeProperties properties;
    private final TaskExecutor analysisExecutor;

    public PracticeRecordingService(PracticeRecordingRepository recordingRepository,
            PracticeFeedbackRepository feedbackRepository, PracticeAnalysisService analysisService,
            PracticeAnalysisClient analysisClient, FileService fileService, PracticeProperties properties,
            @Qualifier(PracticeAnalysisConfig.EXECUTOR_BEAN_NAME) TaskExecutor analysisExecutor) {
        this.recordingRepository = recordingRepository;
        this.feedbackRepository = feedbackRepository;
        this.analysisService = analysisService;
        this.analysisClient = analysisClient;
        this.fileService = fileService;
        this.properties = properties;
        this.analysisExecutor = analysisExecutor;
    }

    /**
     * 분석 요청. 입력 오류(400)를 상태 충돌(409)보다 먼저 본다. 진행 중 확인은 사전 검증이고, 같은 사람의 요청이 정확히 겹치면
     * practice_recordings의 생성 컬럼 유니크가 막아 같은 409가 된다.
     */
    @Transactional
    public PracticeRecordingSummaryResponse create(Long userId, PracticeRecordingCreateRequest request) {
        String fileKey = fileService.findOwnedFileKey(request.audioUrl(), UploadDomain.PRACTICE_AUDIO, userId)
                .orElseThrow(() -> invalidAudio("내가 올린 연습 녹음 파일이 아닙니다."));
        ensureUploadedWithinLimit(fileKey);
        ensureNoAnalysisInProgress(userId);

        PracticeRecording saved = flushOrConflict(
                () -> recordingRepository.saveAndFlush(
                        PracticeRecording.create(userId, request.title(), request.part(), fileKey)));
        submitAfterCommit(saved.getId());
        return PracticeRecordingSummaryResponse.from(saved);
    }

    // 단일 Page 조회(내용 + count)라 트랜잭션을 열지 않는다(NotificationService.getNotifications와 같음).
    public PageResponse<PracticeRecordingSummaryResponse> getMyRecordings(Long userId, Pageable pageable) {
        Pageable latestFirst = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), LATEST_FIRST);
        return PageResponse.from(recordingRepository.findByUserId(userId, latestFirst)
                .map(PracticeRecordingSummaryResponse::from));
    }

    // 녹음과 피드백 두 쿼리가 같은 스냅샷이어야 한다 — 분석이 끝나는 순간과 겹치면 DONE인데 피드백이 비어 보일 수 있다.
    @Transactional(readOnly = true)
    public PracticeRecordingDetailResponse getDetail(Long userId, Long recordingId) {
        PracticeRecording recording = recordingRepository.findById(recordingId)
                .filter(found -> found.isOwnedBy(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.PRACTICE_RECORDING_NOT_FOUND));
        List<PracticeFeedback> feedbacks = recording.isDone()
                ? feedbackRepository.findByRecordingIdOrderByStartSecAscIdAsc(recordingId)
                : List.of();
        String audioUrl = fileService.issueReadUrl(recording.getAudioFileKey(), audioUrlExpiry());
        return PracticeRecordingDetailResponse.of(recording, audioUrl, feedbacks);
    }

    /** 실패한 녹음을 같은 파일로 다시 분석한다. 잠금 조회로 시작해 같은 녹음의 다시 요청·삭제와 차례로 처리된다. */
    @Transactional
    public PracticeRecordingSummaryResponse retry(Long userId, Long recordingId) {
        PracticeRecording recording = findOwnedForUpdate(userId, recordingId);
        if (!recording.isFailed()) {
            throw new BusinessException(ErrorCode.INVALID_STATE);
        }
        ensureNoAnalysisInProgress(userId);

        recording.retry();
        flushOrConflict(() -> {
            recordingRepository.flush();
            return recording;
        });
        submitAfterCommit(recordingId);
        return PracticeRecordingSummaryResponse.from(recording);
    }

    /**
     * 녹음과 피드백을 지운다(hard delete). 어느 상태든 지울 수 있다 — 분석 중이면 늦게 온 결과는 버려진다
     * (PracticeAnalysisService.complete가 같은 행을 잠금 조회한다). 녹음 파일은 지우지 않는다(명세 — 파일 삭제 API로 따로).
     */
    @Transactional
    public void delete(Long userId, Long recordingId) {
        PracticeRecording recording = findOwnedForUpdate(userId, recordingId);
        feedbackRepository.deleteByRecordingId(recordingId);
        recordingRepository.delete(recording);
    }

    /**
     * 회원 탈퇴 때 이 사람의 연습 녹음을 전부 지운다(1팀 UserWithdrawService가 직책 확인을 통과한 뒤 부른다, PR #157 리뷰).
     * 부르는 쪽 트랜잭션에 참여한다 — 탈퇴가 롤백되면 같이 되돌아간다. 녹음·피드백은 hard delete하고, 녹음 파일도 files 행과
     * 저장소 객체까지 지운다(저장소는 커밋 뒤). 단건 삭제(API)와 달리 파일까지 지우는 이유: 탈퇴한 사람은 파일 삭제 API를 부를 수
     * 없어 파일이 영영 남는다. 분석 중인 녹음도 지운다 — 녹음 행을 잠금 조회로 잡아, 겹친 분석 완료는 탈퇴가 커밋된 뒤 빈 결과를 보고
     * 버린다(delete와 같음). 대기열에 남은 건은 분석을 시작할 때 행이 없어 건너뛴다.
     */
    @Transactional
    public void deleteAllOf(Long userId) {
        List<PracticeRecording> recordings = recordingRepository.findAllByUserIdForUpdate(userId);
        if (recordings.isEmpty()) {
            return;
        }
        feedbackRepository.deleteByRecordingIdIn(recordings.stream().map(PracticeRecording::getId).toList());
        recordingRepository.deleteAllInBatch(recordings);
        fileService.deleteOwnedFiles(userId, recordings.stream().map(PracticeRecording::getAudioFileKey).toList());
    }

    /**
     * 실행기 스레드에서 도는 분석 한 건. 트랜잭션이 없다 — AI를 기다리는 동안(수 분) DB 커넥션을 쥐지 않는다.
     * 상태 전이는 짧은 트랜잭션 3개(시작·완료·실패)로 PracticeAnalysisService가 한다.
     */
    void runAnalysis(Long recordingId) {
        Optional<PracticeRecording> started;
        try {
            started = analysisService.start(recordingId);
        } catch (RuntimeException e) {
            log.error("연습 녹음 분석을 시작하지 못했습니다. recordingId={}", recordingId, e);
            failQuietly(recordingId, REASON_ANALYSIS_FAILED);
            return;
        }
        if (started.isEmpty()) {
            log.info("분석 시작 전에 지워졌거나 이미 처리된 연습 녹음입니다. recordingId={}", recordingId);
            return;
        }

        PracticeRecording recording = started.get();
        try {
            String audioUrl = fileService.issueReadUrl(recording.getAudioFileKey(), audioUrlExpiry());
            List<AnalysisFeedback> results = analysisClient.analyze(new AnalysisRequest(
                    audioUrl, extensionOf(recording.getAudioFileKey()), recording.getPart()));
            analysisService.complete(recordingId, results);
        } catch (RuntimeException e) {
            log.warn("연습 녹음 분석에 실패했습니다. recordingId={}", recordingId, e);
            failQuietly(recordingId, reasonOf(e));
        }
    }

    // 커밋된 뒤에만 실행기에 넘긴다. 롤백되면 넘기지 않는다.
    private void submitAfterCommit(Long recordingId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    analysisExecutor.execute(() -> runAnalysis(recordingId));
                } catch (TaskRejectedException e) {
                    log.warn("분석 대기열이 가득 차 연습 녹음 분석을 시작하지 못했습니다. recordingId={}", recordingId);
                    failQuietly(recordingId, REASON_BUSY);
                }
            }
        });
    }

    // 실패 기록마저 실패하면(DB 장애 등) 녹음은 진행 중으로 남고 다음 부팅 정리 때 실패로 바뀐다 — 로그만 남긴다.
    private void failQuietly(Long recordingId, String reason) {
        try {
            analysisService.fail(recordingId, reason);
        } catch (RuntimeException e) {
            log.error("연습 녹음을 실패로 바꾸지 못했습니다. recordingId={}", recordingId, e);
        }
    }

    // RestClient의 연결 실패·응답 대기 초과는 ResourceAccessException이다. 그 밖(AI 오류 응답·응답 해석 실패 등)은 분석 실패.
    private static String reasonOf(RuntimeException e) {
        return e instanceof ResourceAccessException ? REASON_AI_UNREACHABLE : REASON_ANALYSIS_FAILED;
    }

    private void ensureUploadedWithinLimit(String fileKey) {
        OptionalLong size = fileService.findSize(fileKey);
        if (size.isEmpty()) {
            throw invalidAudio("업로드가 끝나지 않은 파일입니다.");
        }
        long maxBytes = fileService.maxUploadBytes(UploadDomain.PRACTICE_AUDIO);
        if (size.getAsLong() > maxBytes) {
            throw invalidAudio("%dMB를 넘는 파일입니다.".formatted(maxBytes / (1024 * 1024)));
        }
    }

    private void ensureNoAnalysisInProgress(Long userId) {
        if (recordingRepository.existsByUserIdAndStatusIn(userId, PracticeRecordingStatus.IN_PROGRESS)) {
            throw new BusinessException(ErrorCode.PRACTICE_ANALYSIS_IN_PROGRESS);
        }
    }

    private PracticeRecording findOwnedForUpdate(Long userId, Long recordingId) {
        return recordingRepository.findByIdForUpdate(recordingId)
                .filter(found -> found.isOwnedBy(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.PRACTICE_RECORDING_NOT_FOUND));
    }

    // 사전 검증을 통과한 같은 사람의 요청이 정확히 겹치면 진행 중 유니크에 걸린다 — 500 대신 409. 다른 제약 위반은 그대로 던진다.
    private static PracticeRecording flushOrConflict(Supplier<PracticeRecording> write) {
        try {
            return write.get();
        } catch (DataIntegrityViolationException e) {
            String message = e.getMostSpecificCause().getMessage();
            if (message != null && message.contains(ACTIVE_USER_UNIQUE_INDEX)) {
                throw new BusinessException(ErrorCode.PRACTICE_ANALYSIS_IN_PROGRESS);
            }
            throw e;
        }
    }

    private static BusinessException invalidAudio(String reason) {
        return new BusinessException(ErrorCode.VALIDATION_FAILED, "audioUrl: " + reason);
    }

    private Duration audioUrlExpiry() {
        return Duration.ofMinutes(properties.audioUrlExpiryMinutes());
    }

    private static String extensionOf(String fileKey) {
        return fileKey.substring(fileKey.lastIndexOf('.') + 1);
    }
}

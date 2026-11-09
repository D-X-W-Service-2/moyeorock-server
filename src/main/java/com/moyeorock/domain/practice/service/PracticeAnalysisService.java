package com.moyeorock.domain.practice.service;

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
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 분석 한 건의 상태 전이(시작·완료·실패)와 부팅 때 정리. 분석 실행(PracticeRecordingService.runAnalysis)은
 * "짧은 트랜잭션 → 트랜잭션 없이 AI 대기(수 분) → 짧은 트랜잭션"이라, 같은 클래스 안에서 부르면 프록시를 안 타 트랜잭션이
 * 걸리지 않는 단계들을 여기로 뗐다. 리포지토리만 쓰고 다른 Service를 부르지 않는다(순환 없음).
 *
 * <p>세 전이 모두 녹음 행을 잠금 조회로 시작한다 — 같은 녹음의 삭제와 차례로 처리돼, 분석 중에 지운 녹음의 결과는 버려진다.
 * 또 모두 {@code REQUIRES_NEW}다 — 요청 트랜잭션의 커밋 후 콜백(afterCommit)에서 불려도 이미 끝난 트랜잭션에 섞이지 않고
 * 따로 커밋돼야 하기 때문이다(NotificationService.saveOnEvent와 같은 이유).
 */
@Slf4j
@Service
public class PracticeAnalysisService {

    static final String REASON_RESTARTED = "서버 재시작으로 분석이 중단되었습니다. 다시 요청해 주세요.";

    private final PracticeRecordingRepository recordingRepository;
    private final PracticeFeedbackRepository feedbackRepository;

    // 이 빈이 만들어지는 시각 = 웹 서버가 요청을 받기 전. 이보다 먼저 바뀐 진행 중 녹음만 부팅 때 실패로 정리한다.
    private final LocalDateTime bootedAt;

    public PracticeAnalysisService(PracticeRecordingRepository recordingRepository,
            PracticeFeedbackRepository feedbackRepository) {
        this.recordingRepository = recordingRepository;
        this.feedbackRepository = feedbackRepository;
        this.bootedAt = LocalDateTime.now();
    }

    /**
     * 대기 중(SUBMITTED)인 녹음을 분석 중으로 바꾸고 돌려준다(AI 요청에 녹음 파일 key와 세션을 쓴다 — 트랜잭션이 끝난
     * 엔티티라 단순 필드만 읽는다). 그새 지워졌거나 대기 중이 아니면(부팅 정리로 실패 처리된 경우 등) 빈 값 — 분석하지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<PracticeRecording> start(Long recordingId) {
        return recordingRepository.findByIdForUpdate(recordingId)
                .filter(PracticeRecording::isSubmitted)
                .map(recording -> {
                    recording.startAnalysis();
                    return recording;
                });
    }

    /**
     * AI 결과를 저장하고 분석 완료로 바꾼다. 분석 중에 지워졌거나 분석 중이 아니면 결과를 버리고 false.
     * 세션 값을 알 수 없거나 구간·내용이 잘못된 줄은 그 줄만 버린다 — 한 줄 때문에 분석 전체를 실패로 만들지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean complete(Long recordingId, List<AnalysisFeedback> results) {
        Optional<PracticeRecording> found = recordingRepository.findByIdForUpdate(recordingId)
                .filter(PracticeRecording::isAnalyzing);
        if (found.isEmpty()) {
            log.info("분석 중에 지워졌거나 상태가 바뀐 연습 녹음이라 결과를 버립니다. recordingId={}", recordingId);
            return false;
        }
        List<PracticeFeedback> feedbacks = results.stream()
                .map(result -> toFeedback(recordingId, result))
                .flatMap(Optional::stream)
                .toList();
        feedbackRepository.saveAll(feedbacks);
        found.get().complete(LocalDateTime.now());
        return true;
    }

    /** 진행 중인 녹음을 실패로 바꾼다. 그새 지워졌거나 이미 끝났으면 아무것도 하지 않는다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long recordingId, String reason) {
        recordingRepository.findByIdForUpdate(recordingId)
                .filter(PracticeRecording::isInProgress)
                .ifPresent(recording -> recording.fail(reason));
    }

    /**
     * 서버 재시작(CD 배포) 정리. 실행기 대기열은 메모리라 재시작하면 사라지고 분석 중이던 AI 호출도 끊기므로, 부팅 전에
     * 마지막으로 바뀐 진행 중 녹음을 실패로 바꾼다 — 사용자가 다시 요청한다. 서버가 1대라는 전제다(여러 대면 다른 서버가
     * 하던 분석까지 끊는다).
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void failUnfinishedOnStartup() {
        int count = recordingRepository.failUnfinishedBefore(bootedAt, REASON_RESTARTED, LocalDateTime.now(),
                PracticeRecordingStatus.FAILED, PracticeRecordingStatus.IN_PROGRESS);
        if (count > 0) {
            log.warn("서버 재시작으로 중단된 연습 녹음 분석 {}건을 실패로 바꿨습니다.", count);
        }
    }

    private Optional<PracticeFeedback> toFeedback(Long recordingId, AnalysisFeedback result) {
        Optional<Instrument> part = parsePart(result.part());
        boolean validRange = result.startSec() >= 0 && result.endSec() >= result.startSec();
        if (part.isEmpty() || !validRange || !StringUtils.hasText(result.content())) {
            log.warn("AI 분석 결과 한 줄을 버립니다(세션 값·구간·내용 확인 필요). recordingId={}, result={}", recordingId, result);
            return Optional.empty();
        }
        return Optional.of(PracticeFeedback.create(
                recordingId, result.startSec(), result.endSec(), part.get(), result.content()));
    }

    private static Optional<Instrument> parsePart(String part) {
        if (part == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instrument.valueOf(part));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}

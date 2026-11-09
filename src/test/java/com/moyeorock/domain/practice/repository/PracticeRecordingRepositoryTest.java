package com.moyeorock.domain.practice.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.domain.practice.entity.PracticeFeedback;
import com.moyeorock.domain.practice.entity.PracticeRecording;
import com.moyeorock.domain.practice.enums.PracticeRecordingStatus;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.config.JpaAuditingConfig;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * 연습 녹음 쿼리를 실제 MySQL에서 본다. 특히 "한 사람당 진행 중 분석 1개"를 막는 생성 컬럼 유니크
 * (V13 active_user_id)와 부팅 정리 쿼리의 updated_at 기준은 SQL로만 확인할 수 있다.
 */
@DataJpaTest
@Import({JpaAuditingConfig.class, TestcontainersConfig.class})
class PracticeRecordingRepositoryTest {

    private static final Long ME = 1L;
    private static final Long OTHER = 2L;
    private static final Sort LATEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    @Autowired
    private PracticeRecordingRepository recordingRepository;

    @Autowired
    private PracticeFeedbackRepository feedbackRepository;

    @Test
    @DisplayName("내 목록은 내 녹음만 최근 요청 순으로, 페이지 count까지 맞게 준다")
    void findByUserId_returnsMineLatestFirst() throws InterruptedException {
        PracticeRecording oldest = recordingRepository.saveAndFlush(finished(ME, "1번", PracticeRecordingStatus.DONE));
        Thread.sleep(5);
        PracticeRecording middle = recordingRepository.saveAndFlush(finished(ME, "2번", PracticeRecordingStatus.FAILED));
        Thread.sleep(5);
        PracticeRecording newest = recordingRepository.saveAndFlush(PracticeRecording.create(ME, "3번", "practice/c.m4a"));
        recordingRepository.saveAndFlush(PracticeRecording.create(OTHER, "남의 것", "practice/x.m4a"));

        Page<PracticeRecording> first = recordingRepository.findByUserId(ME, PageRequest.of(0, 2, LATEST_FIRST));
        Page<PracticeRecording> second = recordingRepository.findByUserId(ME, PageRequest.of(1, 2, LATEST_FIRST));

        assertThat(first.getContent()).extracting(PracticeRecording::getId).containsExactly(newest.getId(), middle.getId());
        assertThat(second.getContent()).extracting(PracticeRecording::getId).containsExactly(oldest.getId());
        assertThat(first.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("진행 중 확인은 SUBMITTED·ANALYZING만 참이다")
    void existsByUserIdAndStatusIn_onlyInProgress() {
        recordingRepository.saveAndFlush(finished(ME, "끝남", PracticeRecordingStatus.DONE));
        recordingRepository.saveAndFlush(finished(ME, "실패", PracticeRecordingStatus.FAILED));
        assertThat(recordingRepository.existsByUserIdAndStatusIn(ME, PracticeRecordingStatus.IN_PROGRESS)).isFalse();

        PracticeRecording analyzing = PracticeRecording.create(ME, "분석 중", "practice/a.m4a");
        analyzing.startAnalysis();
        recordingRepository.saveAndFlush(analyzing);
        assertThat(recordingRepository.existsByUserIdAndStatusIn(ME, PracticeRecordingStatus.IN_PROGRESS)).isTrue();
        assertThat(recordingRepository.existsByUserIdAndStatusIn(OTHER, PracticeRecordingStatus.IN_PROGRESS)).isFalse();
    }

    @Test
    @DisplayName("같은 사람의 진행 중 녹음이 둘이 되면 DB 유니크가 막는다")
    void activeUserUnique_blocksSecondInProgress() {
        recordingRepository.saveAndFlush(PracticeRecording.create(ME, "첫 번째", "practice/a.m4a"));

        assertThatThrownBy(() -> recordingRepository.saveAndFlush(
                PracticeRecording.create(ME, "두 번째", "practice/b.m4a")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_practice_recordings_active_user_id");
    }

    @Test
    @DisplayName("끝난(DONE·FAILED) 녹음은 여러 개여도 되고, 다른 사람은 각자 진행 중 1개를 가질 수 있다")
    void activeUserUnique_allowsFinishedAndOtherUsers() {
        recordingRepository.saveAndFlush(finished(ME, "끝남 1", PracticeRecordingStatus.DONE));
        recordingRepository.saveAndFlush(finished(ME, "끝남 2", PracticeRecordingStatus.DONE));
        recordingRepository.saveAndFlush(finished(ME, "실패", PracticeRecordingStatus.FAILED));
        recordingRepository.saveAndFlush(PracticeRecording.create(ME, "진행 중", "practice/a.m4a"));
        recordingRepository.saveAndFlush(PracticeRecording.create(OTHER, "남의 진행 중", "practice/b.m4a"));

        assertThat(recordingRepository.count()).isEqualTo(5);
    }

    @Test
    @DisplayName("실패한 녹음을 다시 요청(SUBMITTED)할 때도 다른 진행 중 녹음이 있으면 유니크가 막는다")
    void activeUserUnique_blocksRetryWhileAnotherInProgress() {
        PracticeRecording failed = recordingRepository.saveAndFlush(finished(ME, "실패", PracticeRecordingStatus.FAILED));
        recordingRepository.saveAndFlush(PracticeRecording.create(ME, "진행 중", "practice/b.m4a"));

        failed.retry();

        assertThatThrownBy(() -> recordingRepository.flush())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_practice_recordings_active_user_id");
    }

    @Test
    @DisplayName("부팅 정리는 부팅 전에 바뀐 진행 중 녹음만 실패로 바꾸고, 부팅 뒤 요청·끝난 녹음은 그대로 둔다")
    void failUnfinishedBefore_onlyTouchesInProgressBeforeBoot() throws InterruptedException {
        PracticeRecording submitted = recordingRepository.saveAndFlush(PracticeRecording.create(ME, "대기", "practice/a.m4a"));
        PracticeRecording analyzingBefore = PracticeRecording.create(OTHER, "분석 중", "practice/b.m4a");
        analyzingBefore.startAnalysis();
        recordingRepository.saveAndFlush(analyzingBefore);
        PracticeRecording done = recordingRepository.saveAndFlush(finished(ME, "끝남", PracticeRecordingStatus.DONE));
        Thread.sleep(5);
        LocalDateTime bootedAt = LocalDateTime.now();
        Thread.sleep(5);
        PracticeRecording afterBoot = recordingRepository.saveAndFlush(PracticeRecording.create(3L, "부팅 뒤", "practice/c.m4a"));

        int count = recordingRepository.failUnfinishedBefore(bootedAt, "재시작", LocalDateTime.now(),
                PracticeRecordingStatus.FAILED, PracticeRecordingStatus.IN_PROGRESS);

        assertThat(count).isEqualTo(2);
        assertThat(reload(submitted)).extracting(PracticeRecording::getStatus, PracticeRecording::getFailureReason)
                .containsExactly(PracticeRecordingStatus.FAILED, "재시작");
        assertThat(reload(analyzingBefore).getStatus()).isEqualTo(PracticeRecordingStatus.FAILED);
        assertThat(reload(done).getStatus()).isEqualTo(PracticeRecordingStatus.DONE);
        assertThat(reload(afterBoot).getStatus()).isEqualTo(PracticeRecordingStatus.SUBMITTED);
    }

    @Test
    @DisplayName("피드백은 구간 시작 순으로 나오고, 녹음 id로 한 번에 지운다")
    void feedbacks_orderedByStartSec_andDeletedByRecording() {
        Long recordingId = recordingRepository.saveAndFlush(finished(ME, "끝남", PracticeRecordingStatus.DONE)).getId();
        feedbackRepository.saveAll(List.of(
                PracticeFeedback.create(recordingId, 80, 95, Instrument.VOCAL, "세 번째"),
                PracticeFeedback.create(recordingId, 12, 20, Instrument.VOCAL, "첫 번째"),
                PracticeFeedback.create(recordingId, 45, 58, Instrument.EL_GT, "두 번째"),
                PracticeFeedback.create(999L, 0, 1, Instrument.DRUM, "다른 녹음")));
        feedbackRepository.flush();

        assertThat(feedbackRepository.findByRecordingIdOrderByStartSecAscIdAsc(recordingId))
                .extracting(PracticeFeedback::getContent)
                .containsExactly("첫 번째", "두 번째", "세 번째");

        assertThat(feedbackRepository.deleteByRecordingId(recordingId)).isEqualTo(3);
        assertThat(feedbackRepository.findByRecordingIdOrderByStartSecAscIdAsc(recordingId)).isEmpty();
        assertThat(feedbackRepository.findByRecordingIdOrderByStartSecAscIdAsc(999L)).hasSize(1);
    }

    private PracticeRecording reload(PracticeRecording recording) {
        return recordingRepository.findById(recording.getId()).orElseThrow();
    }

    private static PracticeRecording finished(Long userId, String title, PracticeRecordingStatus status) {
        PracticeRecording recording = PracticeRecording.create(userId, title, "practice/" + title + ".m4a");
        recording.startAnalysis();
        if (status == PracticeRecordingStatus.DONE) {
            recording.complete(LocalDateTime.now());
        } else {
            recording.fail("실패 사유");
        }
        return recording;
    }
}

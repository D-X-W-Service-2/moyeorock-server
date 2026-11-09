package com.moyeorock.domain.practice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.domain.practice.client.AnalysisFeedback;
import com.moyeorock.domain.practice.client.AnalysisRequest;
import com.moyeorock.domain.practice.client.PracticeAnalysisClient;
import com.moyeorock.domain.practice.config.PracticeAnalysisConfig;
import com.moyeorock.domain.practice.dto.request.PracticeRecordingCreateRequest;
import com.moyeorock.domain.practice.entity.PracticeRecording;
import com.moyeorock.domain.practice.enums.PracticeRecordingStatus;
import com.moyeorock.domain.practice.repository.PracticeFeedbackRepository;
import com.moyeorock.domain.practice.repository.PracticeRecordingRepository;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import com.moyeorock.global.file.client.FileStorageClient;
import com.moyeorock.global.file.enums.UploadDomain;
import com.moyeorock.global.file.repository.UploadedFileRepository;
import com.moyeorock.global.file.service.FileService;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 커밋 후 실행기로 넘어가는 분석 흐름을 실제 MySQL·실제 단일 스레드 실행기로 본다. 요청이 따로 커밋돼야 하므로 클래스에
 * {@code @Transactional}을 붙이지 않고 남은 행을 직접 지운다(GroupWithdrawConcurrencyTest와 같은 방식). AI는
 * {@code @MockitoBean}으로 바꿔 끼워 결과·지연·대기를 테스트가 정한다. 기다릴 때는 마감 시간이 있는 짧은 대기 루프를 쓴다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
class PracticeAnalysisFlowTest {

    private static final long TIMEOUT_MILLIS = 10_000;

    @MockitoBean
    private PracticeAnalysisClient analysisClient;

    @Autowired
    private PracticeRecordingService recordingService;

    @Autowired
    private PracticeRecordingRepository recordingRepository;

    @Autowired
    private PracticeFeedbackRepository feedbackRepository;

    @Autowired
    private FileService fileService;

    @Autowired
    private FileStorageClient fileStorageClient;

    @Autowired
    private UploadedFileRepository uploadedFileRepository;

    @Autowired
    @Qualifier(PracticeAnalysisConfig.EXECUTOR_BEAN_NAME)
    private ThreadPoolTaskExecutor analysisExecutor;

    @AfterEach
    void cleanUp() {
        awaitIdle();
        feedbackRepository.deleteAllInBatch();
        recordingRepository.deleteAllInBatch();
        uploadedFileRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("요청이 커밋되면 실행기가 임시 URL로 AI를 부르고, 결과를 저장해 분석 완료가 된다")
    void create_runsAnalysisAfterCommit_andSavesResult() {
        when(analysisClient.analyze(any())).thenReturn(List.of(
                new AnalysisFeedback(80, 95, "VOCAL", "음정이 2도 플랫되었습니다"),
                new AnalysisFeedback(10, 12, "DRUM", "박자가 빠릅니다")));

        Long id = recordingService.create(101L, request(uploadAudio(101L))).id();

        PracticeRecording done = awaitStatus(id, PracticeRecordingStatus.DONE);
        assertThat(done.getAnalyzedAt()).isNotNull();
        assertThat(feedbackRepository.findByRecordingIdOrderByStartSecAscIdAsc(id))
                .extracting(feedback -> feedback.getStartSec())
                .containsExactly(10, 80);
        ArgumentCaptor<AnalysisRequest> captor = ArgumentCaptor.forClass(AnalysisRequest.class);
        verify(analysisClient).analyze(captor.capture());
        assertThat(captor.getValue().audioUrl()).contains("/practice/");
        assertThat(captor.getValue().fileExtension()).isEqualTo("m4a");
    }

    @Test
    @DisplayName("같은 사람이 동시에 두 번 요청하면 하나만 접수되고 하나는 409 PRACTICE_ANALYSIS_IN_PROGRESS")
    void create_concurrentRequestsBySameUser_acceptsOnlyOne() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        when(analysisClient.analyze(any())).thenAnswer(invocation -> {
            release.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);   // 첫 분석이 끝나 버리면 두 번째도 통과하므로 붙잡아 둔다
            return List.of();
        });
        String first = uploadAudio(102L);
        String second = uploadAudio(102L);
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService requests = Executors.newFixedThreadPool(2);

        try {
            List<Future<Object>> results = new ArrayList<>();
            for (String audioUrl : List.of(first, second)) {
                results.add(requests.submit(() -> {
                    startGate.await();
                    try {
                        return recordingService.create(102L, request(audioUrl));
                    } catch (BusinessException e) {
                        return e.getErrorCode();
                    }
                }));
            }
            startGate.countDown();

            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> result : results) {
                outcomes.add(result.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            }
            assertThat(outcomes).filteredOn(ErrorCode.PRACTICE_ANALYSIS_IN_PROGRESS::equals).hasSize(1);
            assertThat(recordingRepository.count()).isEqualTo(1);
        } finally {
            release.countDown();
            requests.shutdownNow();
        }
    }

    @Test
    @DisplayName("여러 사람이 요청해도 AI는 한 번에 하나씩만 불린다")
    void analyses_runOneAtATime() {
        AtomicInteger running = new AtomicInteger();
        AtomicInteger maxRunning = new AtomicInteger();
        when(analysisClient.analyze(any())).thenAnswer(invocation -> {
            maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
            Thread.sleep(100);
            running.decrementAndGet();
            return List.of();
        });

        List<Long> ids = new ArrayList<>();
        for (long userId = 201L; userId <= 203L; userId++) {
            ids.add(recordingService.create(userId, request(uploadAudio(userId))).id());
        }

        ids.forEach(id -> awaitStatus(id, PracticeRecordingStatus.DONE));
        assertThat(maxRunning.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("분석 중에 지운 녹음은 늦게 온 결과가 버려져 피드백이 남지 않는다")
    void delete_duringAnalysis_discardsLateResult() throws InterruptedException {
        CountDownLatch analysisStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(analysisClient.analyze(any())).thenAnswer(invocation -> {
            analysisStarted.countDown();
            release.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            return List.of(new AnalysisFeedback(0, 5, "VOCAL", "늦게 온 결과"));
        });
        Long id = recordingService.create(301L, request(uploadAudio(301L))).id();
        assertThat(analysisStarted.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue();

        recordingService.delete(301L, id);
        release.countDown();
        awaitIdle();

        assertThat(recordingRepository.findById(id)).isEmpty();
        assertThat(feedbackRepository.findByRecordingIdOrderByStartSecAscIdAsc(id)).isEmpty();
    }

    private static PracticeRecordingCreateRequest request(String audioUrl) {
        return new PracticeRecordingCreateRequest("연습 녹음", audioUrl);
    }

    // 업로드 URL을 발급받고 실제로 파일을 올린 것처럼 저장소에 써 둔다(테스트 설정은 로컬 저장).
    private String uploadAudio(long userId) {
        String fileUrl = fileService.issuePresignedUrl(UploadDomain.PRACTICE_AUDIO, "practice.m4a", "audio/mp4", userId)
                .fileUrl();
        fileStorageClient.write(fileStorageClient.toFileKey(fileUrl).orElseThrow(),
                new ByteArrayInputStream(new byte[]{1, 2, 3}));
        return fileUrl;
    }

    private PracticeRecording awaitStatus(Long id, PracticeRecordingStatus expected) {
        long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            PracticeRecording recording = recordingRepository.findById(id).orElseThrow();
            if (recording.getStatus() == expected) {
                return recording;
            }
            sleepBriefly();
        }
        return fail("연습 녹음 %d이 %s가 되지 않았습니다".formatted(id, expected));
    }

    // 실행기에 넘긴 작업이 모두 끝날 때까지 기다린다(넘긴 수 = 끝난 수).
    private void awaitIdle() {
        long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
        var pool = analysisExecutor.getThreadPoolExecutor();
        while (pool.getCompletedTaskCount() != pool.getTaskCount()) {
            if (System.currentTimeMillis() > deadline) {
                fail("분석 실행기가 끝나지 않았습니다");
            }
            sleepBriefly();
        }
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}

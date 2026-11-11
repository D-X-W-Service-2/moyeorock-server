package com.moyeorock.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.domain.practice.entity.PracticeFeedback;
import com.moyeorock.domain.practice.entity.PracticeRecording;
import com.moyeorock.domain.practice.repository.PracticeFeedbackRepository;
import com.moyeorock.domain.practice.repository.PracticeRecordingRepository;
import com.moyeorock.domain.team.dto.request.TeamCreateRequest;
import com.moyeorock.domain.team.service.TeamService;
import com.moyeorock.domain.user.entity.User;
import com.moyeorock.domain.user.repository.UserRepository;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Region;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import com.moyeorock.global.file.client.FileStorageClient;
import com.moyeorock.global.file.enums.UploadDomain;
import com.moyeorock.global.file.repository.UploadedFileRepository;
import com.moyeorock.global.file.service.FileService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴 때 개인 연습 녹음 정리(PR #157 리뷰). 실제 MySQL에서 탈퇴자의 녹음·피드백·파일 행이 사라지고 다른 사람 것은 남는지,
 * 직책 때문에 막히면 아무것도 지워지지 않는지 본다. 클래스가 {@code @Transactional}이라 저장소 객체 삭제(커밋 뒤)는 여기서 보지
 * 않는다 — FileServiceTest·PracticeAnalysisFlowTest가 본다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@Transactional
class UserWithdrawPracticeCleanupTest {

    @Autowired
    private UserWithdrawService userWithdrawService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TeamService teamService;
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

    @Test
    @DisplayName("탈퇴하면 분석 중인 것까지 내 연습 녹음·피드백·녹음 파일 행이 지워지고, 다른 사람 녹음은 남는다")
    void withdraw_deletesMyPracticeRecordings() {
        Long me = saveUser("탈퇴자").getId();
        Long other = saveUser("남은사람").getId();
        PracticeRecording done = saveRecording(me, true);
        feedbackRepository.save(PracticeFeedback.create(done.getId(), 10, 20, Instrument.VOCAL, "음정이 2도 플랫되었습니다"));
        PracticeRecording analyzing = saveRecording(me, false);
        PracticeRecording othersRecording = saveRecording(other, true);

        userWithdrawService.withdraw(me);

        assertThat(recordingRepository.findAllById(List.of(done.getId(), analyzing.getId()))).isEmpty();
        assertThat(feedbackRepository.findByRecordingIdOrderByStartSecAscIdAsc(done.getId())).isEmpty();
        assertThat(uploadedFileRepository.findByFileKey(done.getAudioFileKey())).isEmpty();
        assertThat(uploadedFileRepository.findByFileKey(analyzing.getAudioFileKey())).isEmpty();
        assertThat(recordingRepository.findById(othersRecording.getId())).isPresent();
        assertThat(uploadedFileRepository.findByFileKey(othersRecording.getAudioFileKey())).isPresent();
    }

    @Test
    @DisplayName("팀장이라 탈퇴가 막히면 연습 녹음은 그대로다")
    void withdraw_blockedByRole_keepsPracticeRecordings() {
        Long me = saveUser("팀장").getId();
        PracticeRecording recording = saveRecording(me, true);
        teamService.create(me, new TeamCreateRequest("무명밴드", null, Region.SEOUL, List.of(Genre.ROCK), Instrument.BASS));

        Throwable thrown = catchThrowable(() -> userWithdrawService.withdraw(me));

        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(ErrorCode.WITHDRAW_BLOCKED_BY_ROLE);
        assertThat(recordingRepository.findById(recording.getId())).isPresent();
        assertThat(uploadedFileRepository.findByFileKey(recording.getAudioFileKey())).isPresent();
    }

    private User saveUser(String nickname) {
        return userRepository.saveAndFlush(
                User.signupWithEmail(nickname + "@example.com", "hash", nickname, LocalDateTime.now()));
    }

    // 업로드 URL을 발급받아 files 행을 만들고 그 key로 녹음을 저장한다. finished면 분석 완료, 아니면 분석 중.
    private PracticeRecording saveRecording(Long userId, boolean finished) {
        String fileUrl = fileService.issuePresignedUrl(UploadDomain.PRACTICE_AUDIO, "practice.m4a", "audio/mp4", userId)
                .fileUrl();
        String fileKey = fileStorageClient.toFileKey(fileUrl).orElseThrow();
        PracticeRecording recording = PracticeRecording.create(userId, "연습", Instrument.VOCAL, fileKey);
        recording.startAnalysis();
        if (finished) {
            recording.complete(LocalDateTime.now());
        }
        return recordingRepository.saveAndFlush(recording);
    }
}

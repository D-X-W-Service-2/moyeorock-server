package com.moyeorock.domain.join.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.domain.join.dto.request.InvitationCreateRequest;
import com.moyeorock.domain.join.dto.request.JoinRequestCreateRequest;
import com.moyeorock.domain.join.enums.InvitationDecision;
import com.moyeorock.domain.join.repository.JoinRequestRepository;
import com.moyeorock.domain.notification.entity.Notification;
import com.moyeorock.domain.notification.repository.NotificationRepository;
import com.moyeorock.domain.team.dto.request.TeamCreateRequest;
import com.moyeorock.domain.team.repository.TeamGenreRepository;
import com.moyeorock.domain.team.repository.TeamMemberRepository;
import com.moyeorock.domain.team.repository.TeamRepository;
import com.moyeorock.domain.team.service.TeamService;
import com.moyeorock.domain.user.entity.User;
import com.moyeorock.domain.user.repository.UserRepository;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.JoinStatus;
import com.moyeorock.global.common.enums.NotificationType;
import com.moyeorock.global.common.enums.Region;
import com.moyeorock.global.common.enums.TargetType;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 알림을 발행하는 경로마다 "커밋되면 알림이 저장된다"를 확인한다(docs/plans/notification-event.md "발행하는 쪽 팀이 지킬 것").
 * 클래스 단위 {@code @Transactional}을 쓰면 AFTER_COMMIT이 호출되지 않으므로 쓰지 않고, 테스트마다 직접 지운다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
class JoinNotificationCommitTest {

    private long leaderId;
    private long userId;
    private long teamId;

    @Autowired
    private JoinRequestService joinRequestService;
    @Autowired
    private TeamService teamService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JoinRequestRepository joinRequestRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private TeamMemberRepository teamMemberRepository;
    @Autowired
    private TeamGenreRepository teamGenreRepository;

    @BeforeEach
    void setUp() {
        leaderId = saveUser("커밋리더").getId();
        userId = saveUser("커밋유저").getId();
        teamId = teamService.create(leaderId,
                new TeamCreateRequest("커밋밴드", null, Region.SEOUL, List.of(Genre.ROCK), Instrument.EL_GT)).id();
    }

    @AfterEach
    void cleanUp() {
        notificationRepository.deleteAllInBatch();
        joinRequestRepository.deleteAllInBatch();
        teamMemberRepository.deleteAllInBatch();
        teamGenreRepository.deleteAllInBatch();
        teamRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    private User saveUser(String nickname) {
        return userRepository.saveAndFlush(User.signupWithEmail(
                nickname + "@example.com", "hash", nickname, LocalDateTime.now()));
    }

    private List<Notification> notificationsOf(long receiverId) {
        return notificationRepository.findAll().stream().filter(n -> n.getUserId().equals(receiverId)).toList();
    }

    @Test
    @DisplayName("신청이 커밋되면 팀장에게, 승인이 커밋되면 신청자에게 알림이 저장된다")
    void applyAndApprove_saveNotifications() {
        Long requestId = joinRequestService.apply(userId,
                new JoinRequestCreateRequest(TargetType.TEAM, teamId, null, Instrument.BASS, "안녕하세요")).id();

        assertThat(notificationsOf(leaderId)).extracting(Notification::getType)
                .containsExactly(NotificationType.TEAM_APPLY);

        joinRequestService.updateStatus(leaderId, requestId, JoinStatus.APPROVED);

        assertThat(notificationsOf(userId)).extracting(Notification::getType)
                .containsExactly(NotificationType.JOIN_REQUEST_APPROVED);
        assertThat(notificationsOf(userId).get(0).getMessage()).contains("커밋밴드");
    }

    @Test
    @DisplayName("초대와 수락이 커밋되면 초대받은 사람에게, 초대한 사람에게 알림이 저장된다")
    void inviteAndAccept_saveNotifications() {
        Long invitationId = joinRequestService.invite(leaderId,
                new InvitationCreateRequest(TargetType.TEAM, teamId, userId, Instrument.DRUM, null)).id();

        assertThat(notificationsOf(userId)).extracting(Notification::getType)
                .containsExactly(NotificationType.TEAM_INVITE);

        joinRequestService.updateInvitationStatus(userId, invitationId, InvitationDecision.ACCEPTED);

        assertThat(notificationsOf(leaderId)).extracting(Notification::getType)
                .containsExactly(NotificationType.INVITATION_ACCEPTED);
    }

    @Test
    @DisplayName("핵심 작업이 롤백되면 알림은 저장되지 않는다 — 이미 처리된 신청을 다시 승인")
    void failedDecision_savesNoNotification() {
        Long requestId = joinRequestService.apply(userId,
                new JoinRequestCreateRequest(TargetType.TEAM, teamId, null, Instrument.BASS, null)).id();
        joinRequestService.updateStatus(leaderId, requestId, JoinStatus.REJECTED);
        int before = notificationsOf(userId).size();

        org.junit.jupiter.api.Assertions.assertThrows(com.moyeorock.global.exception.BusinessException.class,
                () -> joinRequestService.updateStatus(leaderId, requestId, JoinStatus.APPROVED));

        assertThat(notificationsOf(userId)).hasSize(before);
    }
}

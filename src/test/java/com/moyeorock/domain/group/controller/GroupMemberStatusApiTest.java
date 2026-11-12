package com.moyeorock.domain.group.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.domain.group.dto.request.GroupCreateRequest;
import com.moyeorock.domain.group.enums.GroupType;
import com.moyeorock.domain.group.repository.GroupMemberRepository;
import com.moyeorock.domain.group.repository.GroupRepository;
import com.moyeorock.domain.group.service.GroupMemberService;
import com.moyeorock.domain.group.service.GroupService;
import com.moyeorock.domain.notification.entity.Notification;
import com.moyeorock.domain.notification.repository.NotificationRepository;
import com.moyeorock.domain.performance.entity.Performance;
import com.moyeorock.domain.performance.repository.PerformanceRepository;
import com.moyeorock.domain.team.enums.TeamRole;
import com.moyeorock.domain.team.enums.TeamStatus;
import com.moyeorock.domain.team.repository.TeamGenreRepository;
import com.moyeorock.domain.team.repository.TeamMemberRepository;
import com.moyeorock.domain.team.repository.TeamRepository;
import com.moyeorock.domain.team.service.TeamMemberService;
import com.moyeorock.domain.team.service.TeamService;
import com.moyeorock.domain.user.entity.User;
import com.moyeorock.domain.user.repository.UserRepository;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.NotificationTargetType;
import com.moyeorock.global.common.enums.NotificationType;
import com.moyeorock.global.common.enums.Region;
import com.moyeorock.global.security.JwtProvider;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 모임원 내보내기·탈퇴(PATCH /v1/groups/{groupId}/members/{userId}/status)가 그 모임 공연 팀까지 정리하고,
 * 강퇴면 커밋 뒤 알림이 저장되는지 실제 DB에서 확인한다(이슈 #143).
 * 알림은 커밋 후(AFTER_COMMIT)에만 저장되므로 클래스 단위 {@code @Transactional}을 쓰지 않고 테스트마다 직접 지운다
 * (JoinNotificationCommitTest와 같은 방식).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class GroupMemberStatusApiTest {

    private long ownerId;       // 모임장
    private long memberId;      // 내보내지거나 탈퇴하는 모임원
    private long otherOwnerId;  // 다른 모임의 모임장
    private long groupId;
    private long performanceId;
    private long otherTeamId;   // 다른 모임 공연 팀 — memberId가 팀원이고 이번 처리와 무관해야 한다

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtProvider jwtProvider;
    @Autowired
    private GroupService groupService;
    @Autowired
    private GroupMemberService groupMemberService;
    @Autowired
    private TeamService teamService;
    @Autowired
    private TeamMemberService teamMemberService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private GroupMemberRepository groupMemberRepository;
    @Autowired
    private PerformanceRepository performanceRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private TeamMemberRepository teamMemberRepository;
    @Autowired
    private TeamGenreRepository teamGenreRepository;
    @Autowired
    private NotificationRepository notificationRepository;

    @BeforeEach
    void setUp() {
        ownerId = saveUser("모임장").getId();
        memberId = saveUser("모임원").getId();
        otherOwnerId = saveUser("다른모임장").getId();

        groupId = createGroup(ownerId, "홍대 밴드부");
        groupMemberService.addMember(groupId, memberId);
        performanceId = savePerformance(groupId, ownerId);

        long otherGroupId = createGroup(otherOwnerId, "신촌 밴드부");
        groupMemberService.addMember(otherGroupId, memberId);
        otherTeamId = createTeam(otherOwnerId, savePerformance(otherGroupId, otherOwnerId), "신촌팀");
        teamService.addMember(otherTeamId, memberId, Instrument.BASS);
    }

    @AfterEach
    void cleanUp() {
        notificationRepository.deleteAllInBatch();
        teamMemberRepository.deleteAllInBatch();
        teamGenreRepository.deleteAllInBatch();
        teamRepository.deleteAllInBatch();
        performanceRepository.deleteAllInBatch();
        groupMemberRepository.deleteAllInBatch();
        groupRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("모임장이 내보내면 그 모임 공연 팀에서도 빠지고, 커밋 뒤 대상에게 GROUP_MEMBER_REMOVED 알림이 저장된다")
    void kick_removesFromPerformanceTeam_andSavesNotification() throws Exception {
        long teamId = createTeam(ownerId, performanceId, "홍대팀");
        teamService.addMember(teamId, memberId, Instrument.DRUM);

        changeStatus(ownerId, memberId).andExpect(status().isOk());

        assertThat(groupMemberService.isActiveMember(groupId, memberId)).isFalse();
        assertThat(teamMemberService.isActiveMember(teamId, memberId)).isFalse();
        assertThat(teamMemberService.isActiveMember(otherTeamId, memberId)).isTrue();
        assertThat(notificationsOf(memberId))
                .extracting(Notification::getType, Notification::getMessage, Notification::getTargetType,
                        Notification::getTargetId)
                .containsExactly(tuple(NotificationType.GROUP_MEMBER_REMOVED, "홍대 밴드부에서 내보내졌습니다.",
                        NotificationTargetType.GROUP, groupId));
    }

    @Test
    @DisplayName("강퇴 대상이 공연 팀 팀장이면 막히지 않고 가장 먼저 들어온 다른 팀원이 팀장이 되며 알림도 저장된다")
    void kick_teamLeader_passesLeadershipToOldestMember() throws Exception {
        long teamId = createTeam(memberId, performanceId, "모임원이 팀장인 팀");
        teamService.addMember(teamId, ownerId, Instrument.DRUM);

        changeStatus(ownerId, memberId).andExpect(status().isOk());

        assertThat(groupMemberService.isActiveMember(groupId, memberId)).isFalse();
        assertThat(teamMemberService.isActiveMember(teamId, memberId)).isFalse();
        assertThat(teamMemberRepository.findByTeamIdAndUserId(teamId, ownerId).orElseThrow().getRole())
                .isEqualTo(TeamRole.LEADER);
        assertThat(teamRepository.findById(teamId).orElseThrow().getStatus()).isEqualTo(TeamStatus.ACTIVE);
        assertThat(notificationsOf(memberId)).hasSize(1);
    }

    @Test
    @DisplayName("강퇴 대상이 혼자인 공연 팀 팀장이면 그 팀이 해체된다")
    void kick_teamLeaderAlone_disbandsTeam() throws Exception {
        long teamId = createTeam(memberId, performanceId, "모임원만 있는 팀");

        changeStatus(ownerId, memberId).andExpect(status().isOk());

        assertThat(groupMemberService.isActiveMember(groupId, memberId)).isFalse();
        assertThat(teamRepository.findById(teamId).orElseThrow().getStatus()).isEqualTo(TeamStatus.DISBANDED);
    }

    @Test
    @DisplayName("본인 탈퇴인데 공연 팀 팀장이면 409 LEADER_MUST_DELEGATE_FIRST이고 모임원·팀원 상태가 그대로다")
    void leave_teamLeader_returns409_andRollsBackEverything() throws Exception {
        long teamId = createTeam(memberId, performanceId, "모임원이 팀장인 팀");

        changeStatus(memberId, memberId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LEADER_MUST_DELEGATE_FIRST"))
                .andExpect(jsonPath("$.message").value("팀장을 위임한 뒤 탈퇴할 수 있습니다."));

        assertThat(groupMemberService.isActiveMember(groupId, memberId)).isTrue();
        assertThat(teamMemberService.isActiveMember(teamId, memberId)).isTrue();
    }

    @Test
    @DisplayName("본인이 탈퇴하면 그 모임 공연 팀에서도 빠지고 알림은 저장되지 않는다")
    void leave_removesFromPerformanceTeam_withoutNotification() throws Exception {
        long teamId = createTeam(ownerId, performanceId, "홍대팀");
        teamService.addMember(teamId, memberId, Instrument.DRUM);

        changeStatus(memberId, memberId).andExpect(status().isOk());

        assertThat(groupMemberService.isActiveMember(groupId, memberId)).isFalse();
        assertThat(teamMemberService.isActiveMember(teamId, memberId)).isFalse();
        assertThat(teamMemberService.isActiveMember(otherTeamId, memberId)).isTrue();
        assertThat(notificationRepository.findAll()).isEmpty();
    }

    private ResultActions changeStatus(long requesterId, long targetUserId) throws Exception {
        return mockMvc.perform(patch("/v1/groups/" + groupId + "/members/" + targetUserId + "/status")
                .header("Authorization", "Bearer " + jwtProvider.generateToken(requesterId)));
    }

    private User saveUser(String nickname) {
        return userRepository.saveAndFlush(User.signupWithEmail(
                nickname + "@example.com", "hash", nickname, LocalDateTime.now()));
    }

    private long createGroup(long creatorId, String name) {
        return groupService.create(creatorId,
                new GroupCreateRequest(name, null, GroupType.REGULAR, Region.SEOUL, null)).id();
    }

    private long savePerformance(long groupId, long creatorId) {
        return performanceRepository.save(Performance.create(groupId, creatorId, "정기공연", null,
                LocalDateTime.now().plusDays(30), "홍대 클럽", null)).getId();
    }

    private long createTeam(long leaderId, long performanceId, String name) {
        return teamService.createForPerformance(leaderId, performanceId, name, null, Region.SEOUL,
                List.of(Genre.ROCK), Instrument.EL_GT).id();
    }

    private List<Notification> notificationsOf(long receiverId) {
        return notificationRepository.findAll().stream().filter(n -> n.getUserId().equals(receiverId)).toList();
    }
}

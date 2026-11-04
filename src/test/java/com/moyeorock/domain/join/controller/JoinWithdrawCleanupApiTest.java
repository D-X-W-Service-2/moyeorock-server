package com.moyeorock.domain.join.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.domain.group.dto.request.GroupCreateRequest;
import com.moyeorock.domain.group.enums.GroupType;
import com.moyeorock.domain.group.service.GroupMemberService;
import com.moyeorock.domain.group.service.GroupService;
import com.moyeorock.domain.team.dto.request.TeamCreateRequest;
import com.moyeorock.domain.team.service.TeamMemberService;
import com.moyeorock.domain.team.service.TeamService;
import com.moyeorock.domain.user.entity.User;
import com.moyeorock.domain.user.service.UserWithdrawService;
import com.moyeorock.domain.user.repository.UserRepository;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.NotificationType;
import com.moyeorock.global.common.enums.Region;
import com.moyeorock.global.event.NotificationEvent;
import com.moyeorock.global.security.JwtProvider;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/** 회원 탈퇴 시 탈퇴자의 대기 중 신청은 CANCELED, 받은 초대는 REJECTED로 닫힌다(알림 없음). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
@RecordApplicationEvents
class JoinWithdrawCleanupApiTest {

    private long leaderId;
    private long inviteeId;
    private long otherId;
    private long teamId;
    private long groupId;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtProvider jwtProvider;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TeamService teamService;
    @Autowired
    private TeamMemberService teamMemberService;
    @Autowired
    private GroupService groupService;
    @Autowired
    private GroupMemberService groupMemberService;
    @Autowired
    private ApplicationEvents events;
    @Autowired
    private UserWithdrawService userWithdrawService;

    @BeforeEach
    void setUp() {
        leaderId = saveUser("리더").getId();
        inviteeId = saveUser("탈퇴할사람").getId();
        otherId = saveUser("남").getId();
        teamId = teamService.create(leaderId,
                new TeamCreateRequest("무명밴드", null, Region.SEOUL, List.of(Genre.ROCK), Instrument.EL_GT)).id();
        groupId = groupService.create(leaderId,
                new GroupCreateRequest("홍대동아리", null, GroupType.REGULAR, Region.SEOUL, null)).id();
    }

    private User saveUser(String nickname) {
        return userRepository.saveAndFlush(User.signupWithEmail(
                nickname + "@example.com", "hash", nickname, LocalDateTime.now()));
    }

    private ResultActions send(MockHttpServletRequestBuilder builder, long userId, String body) throws Exception {
        builder.header("Authorization", "Bearer " + jwtProvider.generateToken(userId));
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(builder);
    }

    private long create(String url, String body, long userId) throws Exception {
        String json = send(post(url), userId, body).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.data.id")).longValue();
    }

    private long applyToTeam(long userId) throws Exception {
        return create("/v1/join-requests", """
                {"targetType":"TEAM","targetId":%d,"instrument":"DRUM"}
                """.formatted(teamId), userId);
    }

    private long inviteToGroup(long invitee) throws Exception {
        return create("/v1/invitations", """
                {"targetType":"GROUP","targetId":%d,"inviteeId":%d,"instrument":null}
                """.formatted(groupId, invitee), leaderId);
    }

    @Test
    @DisplayName("탈퇴하면 낸 신청은 CANCELED, 받은 초대는 REJECTED가 되고 알림은 더 나가지 않는다")
    void withdraw_closesPendingRequestsAndInvitations() throws Exception {
        long applicationId = applyToTeam(inviteeId);
        long invitationId = inviteToGroup(inviteeId);
        int before = (int) events.stream(NotificationEvent.class).count();

        userWithdrawService.withdraw(inviteeId);

        send(get("/v1/join-requests?targetType=TEAM&targetId=" + teamId + "&status=CANCELED"), leaderId, null)
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].id").value(applicationId));
        send(get("/v1/join-requests?targetType=TEAM&targetId=" + teamId + "&status=PENDING"), leaderId, null)
                .andExpect(jsonPath("$.data.content", hasSize(0)));
        send(get("/v1/invitations?targetType=GROUP&targetId=" + groupId + "&status=REJECTED"), leaderId, null)
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].id").value(invitationId));
        assertThat(events.stream(NotificationEvent.class).count()).isEqualTo(before);
    }

    @Test
    @DisplayName("이미 처리된 건과 다른 사람의 대기 건은 건드리지 않는다")
    void withdraw_leavesOthersAlone() throws Exception {
        long otherApplication = applyToTeam(otherId);
        long mine = applyToTeam(inviteeId);
        send(patch("/v1/join-requests/" + mine + "/status"), inviteeId, "{\"status\":\"CANCELED\"}")
                .andExpect(status().isOk());

        userWithdrawService.withdraw(inviteeId);

        send(get("/v1/join-requests?targetType=TEAM&targetId=" + teamId + "&status=PENDING"), leaderId, null)
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].id").value(otherApplication));
    }

    @Test
    @DisplayName("팀장이 직책 때문에 탈퇴가 막히면 그 사람의 대기 건은 그대로다")
    void blockedWithdraw_keepsPending() throws Exception {
        long groupInvitation = inviteToGroup(inviteeId);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> userWithdrawService.withdraw(leaderId))
                .isInstanceOf(com.moyeorock.global.exception.BusinessException.class);

        send(get("/v1/users/me/invitations?status=PENDING"), inviteeId, null)
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].id").value(groupInvitation));
    }
}

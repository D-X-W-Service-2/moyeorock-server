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
import com.moyeorock.domain.team.dto.request.TeamStatusUpdateRequest;
import com.moyeorock.domain.team.enums.TeamStatus;
import com.moyeorock.domain.team.service.TeamMemberService;
import com.moyeorock.domain.team.service.TeamService;
import com.moyeorock.domain.user.entity.User;
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

/** 해체된 팀·모임 대상의 신청·초대. 거절·취소는 되고 수락·승인은 404이며 목록에는 id만 남은 채 보인다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
@RecordApplicationEvents
class JoinDisbandedTargetApiTest {

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

    @BeforeEach
    void setUp() {
        leaderId = saveUser("리더").getId();
        inviteeId = saveUser("초대받은사람").getId();
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

    private long invite(String type, long targetId) throws Exception {
        String instrument = type.equals("TEAM") ? "\"DRUM\"" : "null";
        return create("/v1/invitations", """
                {"targetType":"%s","targetId":%d,"inviteeId":%d,"instrument":%s}
                """.formatted(type, targetId, inviteeId, instrument), leaderId);
    }

    private long apply(String type, long targetId) throws Exception {
        String instrument = type.equals("TEAM") ? "\"DRUM\"" : "null";
        return create("/v1/join-requests", """
                {"targetType":"%s","targetId":%d,"instrument":%s}
                """.formatted(type, targetId, instrument), inviteeId);
    }

    private ResultActions patchStatus(String path, long userId, String status) throws Exception {
        return send(patch(path), userId, "{\"status\":\"" + status + "\"}");
    }

    private void disbandTeam() {
        teamService.disband(leaderId, teamId, new TeamStatusUpdateRequest(TeamStatus.DISBANDED));
    }

    private void disbandGroup() {
        groupService.disband(leaderId, groupId);
    }

    private boolean notified(NotificationType type, String text) {
        return events.stream(NotificationEvent.class)
                .anyMatch(e -> e.type() == type && e.message().contains(text));
    }

    @Test
    @DisplayName("해체된 팀의 초대를 거절하면 200이고 초대한 사람에게 '해체된 팀'으로 알린다")
    void declineInvitation_disbandedTeam() throws Exception {
        long id = invite("TEAM", teamId);
        disbandTeam();

        patchStatus("/v1/invitations/" + id + "/status", inviteeId, "DECLINED").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
        assertThat(notified(NotificationType.INVITATION_DECLINED, "해체된 팀")).isTrue();
    }

    @Test
    @DisplayName("해체된 모임의 초대를 거절하면 200이다")
    void declineInvitation_disbandedGroup() throws Exception {
        long id = invite("GROUP", groupId);
        disbandGroup();

        patchStatus("/v1/invitations/" + id + "/status", inviteeId, "DECLINED").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
        assertThat(notified(NotificationType.INVITATION_DECLINED, "해체된 모임")).isTrue();
    }

    @Test
    @DisplayName("해체된 팀의 신청은 신청자가 취소할 수 있고, 팀장의 승인·거절은 404다")
    void application_disbandedTeam() throws Exception {
        long id = apply("TEAM", teamId);
        disbandTeam();

        patchStatus("/v1/join-requests/" + id + "/status", leaderId, "REJECTED").andExpect(status().isNotFound());
        patchStatus("/v1/join-requests/" + id + "/status", inviteeId, "CANCELED").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED"));
    }

    @Test
    @DisplayName("해체된 대상의 수락·승인은 실패하고 요청은 대기 상태로 남는다 (팀 409, 모임 404)")
    void accept_disbandedTarget_fails() throws Exception {
        long invitation = invite("TEAM", teamId);
        long application = apply("GROUP", groupId);
        disbandTeam();
        disbandGroup();

        patchStatus("/v1/invitations/" + invitation + "/status", inviteeId, "ACCEPTED")
                .andExpect(status().isConflict());
        patchStatus("/v1/join-requests/" + application + "/status", leaderId, "APPROVED")
                .andExpect(status().isNotFound());

        send(get("/v1/users/me/invitations?status=PENDING"), inviteeId, null)
                .andExpect(jsonPath("$.data.content", hasSize(1)));
    }

    @Test
    @DisplayName("해체된 팀의 초대는 받은 초대 목록에 id만 남은 대상으로 보인다")
    void receivedInvitations_keepDisbandedTarget() throws Exception {
        invite("TEAM", teamId);
        disbandTeam();

        send(get("/v1/users/me/invitations"), inviteeId, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].target.id").value(teamId))
                .andExpect(jsonPath("$.data.content[0].target.name").value(nullValue()));
    }
}

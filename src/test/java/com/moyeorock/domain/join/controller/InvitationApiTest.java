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

/** 초대 흐름. 클래스 단위 {@code @Transactional}이라 알림은 발행된 이벤트로 확인한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
@RecordApplicationEvents
class InvitationApiTest {

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

    private String inviteBody(String targetType, long targetId, long invitee, String instrument) {
        return """
                {"targetType":"%s","targetId":%d,"inviteeId":%d,"instrument":%s,"message":"같이 하실래요?"}
                """.formatted(targetType, targetId, invitee, instrument == null ? "null" : "\"" + instrument + "\"");
    }

    private long inviteToTeam(long inviter, long invitee, String instrument) throws Exception {
        String json = send(post("/v1/invitations"), inviter, inviteBody("TEAM", teamId, invitee, instrument))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.data.id")).longValue();
    }

    private ResultActions decide(long userId, long invitationId, String status) throws Exception {
        return send(patch("/v1/invitations/" + invitationId + "/status"), userId,
                "{\"status\":\"" + status + "\"}");
    }

    @Test
    @DisplayName("팀장이 초대하면 201과 PENDING 초대, 초대받은 사람에게 TEAM_INVITE 이벤트가 간다")
    void invite_team_returns201() throws Exception {
        send(post("/v1/invitations"), leaderId, inviteBody("TEAM", teamId, inviteeId, "DRUM"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.targetType").value("TEAM"))
                .andExpect(jsonPath("$.data.target.name").value("무명밴드"))
                .andExpect(jsonPath("$.data.inviter.id").value(leaderId))
                .andExpect(jsonPath("$.data.invitee.id").value(inviteeId))
                .andExpect(jsonPath("$.data.instrument").value("DRUM"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        List<NotificationEvent> published = events.stream(NotificationEvent.class).toList();
        assertThat(published).hasSize(1);
        assertThat(published.get(0).userId()).isEqualTo(inviteeId);
        assertThat(published.get(0).type()).isEqualTo(NotificationType.TEAM_INVITE);
    }

    @Test
    @DisplayName("모임장이 모임에 초대하면 GROUP_INVITE 이벤트가 가고 instrument는 저장하지 않는다")
    void invite_group_returns201() throws Exception {
        send(post("/v1/invitations"), leaderId, inviteBody("GROUP", groupId, inviteeId, "DRUM"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.target.name").value("홍대동아리"))
                .andExpect(jsonPath("$.data.instrument").value(nullValue()));

        assertThat(events.stream(NotificationEvent.class).map(NotificationEvent::type).toList())
                .containsExactly(NotificationType.GROUP_INVITE);
    }

    @Test
    @DisplayName("팀장이 아니면 403, 없는 팀이면 404, 팀 초대에 instrument가 없으면 400이다")
    void invite_validations() throws Exception {
        send(post("/v1/invitations"), otherId, inviteBody("TEAM", teamId, inviteeId, "DRUM"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_TEAM_LEADER"));
        send(post("/v1/invitations"), leaderId, inviteBody("TEAM", 999_999, inviteeId, "DRUM"))
                .andExpect(status().isNotFound());
        send(post("/v1/invitations"), leaderId, inviteBody("TEAM", teamId, inviteeId, null))
                .andExpect(status().isBadRequest());
        send(post("/v1/invitations"), leaderId, inviteBody("TEAM", teamId, leaderId, "DRUM"))
                .andExpect(status().isBadRequest());
        send(post("/v1/invitations"), leaderId, inviteBody("TEAM", teamId, 999_999, "DRUM"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("이미 팀원이면 409 ALREADY_MEMBER, 대기 중인 초대가 있으면 409 JOIN_REQUEST_DUPLICATED다")
    void invite_conflicts() throws Exception {
        teamService.addMember(teamId, otherId, Instrument.BASS);
        send(post("/v1/invitations"), leaderId, inviteBody("TEAM", teamId, otherId, "DRUM"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_MEMBER"));

        inviteToTeam(leaderId, inviteeId, "DRUM");
        send(post("/v1/invitations"), leaderId, inviteBody("TEAM", teamId, inviteeId, "KEY"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("JOIN_REQUEST_DUPLICATED"));
    }

    @Test
    @DisplayName("신청이 대기 중인 사람은 초대할 수 없다 — 중복 검사는 신청·초대를 가리지 않는다")
    void invite_whenApplicationPending_returns409() throws Exception {
        send(post("/v1/join-requests"), inviteeId,
                "{\"targetType\":\"TEAM\",\"targetId\":" + teamId + ",\"instrument\":\"DRUM\"}")
                .andExpect(status().isCreated());

        send(post("/v1/invitations"), leaderId, inviteBody("TEAM", teamId, inviteeId, "DRUM"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("JOIN_REQUEST_DUPLICATED"));
    }

    @Test
    @DisplayName("수락하면 팀원이 되고 응답 status는 APPROVED이며 초대한 사람에게 수락 이벤트가 간다")
    void accept_addsMember() throws Exception {
        long id = inviteToTeam(leaderId, inviteeId, "DRUM");

        decide(inviteeId, id, "ACCEPTED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.joined.targetType").value("TEAM"))
                .andExpect(jsonPath("$.data.joined.role").value("MEMBER"))
                .andExpect(jsonPath("$.data.joined.instrument").value("DRUM"));

        assertThat(teamMemberService.isActiveMember(teamId, inviteeId)).isTrue();
        assertThat(events.stream(NotificationEvent.class)
                .filter(e -> e.type() == NotificationType.INVITATION_ACCEPTED)
                .map(NotificationEvent::userId).toList()).containsExactly(leaderId);
    }

    @Test
    @DisplayName("모임 초대를 수락하면 모임원이 된다")
    void accept_group_addsMember() throws Exception {
        String json = send(post("/v1/invitations"), leaderId, inviteBody("GROUP", groupId, inviteeId, null))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(json, "$.data.id")).longValue();

        decide(inviteeId, id, "ACCEPTED").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.joined.targetType").value("GROUP"));

        assertThat(groupMemberService.isActiveMember(groupId, inviteeId)).isTrue();
    }

    @Test
    @DisplayName("거절하면 REJECTED 응답이고 초대한 사람에게 거절 이벤트가 간다")
    void decline() throws Exception {
        long id = inviteToTeam(leaderId, inviteeId, "DRUM");

        decide(inviteeId, id, "DECLINED").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        assertThat(teamMemberService.isActiveMember(teamId, inviteeId)).isFalse();
        assertThat(events.stream(NotificationEvent.class)
                .anyMatch(e -> e.type() == NotificationType.INVITATION_DECLINED)).isTrue();
    }

    @Test
    @DisplayName("수락·거절은 초대받은 본인만, 취소는 팀장만 할 수 있다")
    void decide_authorization() throws Exception {
        long id = inviteToTeam(leaderId, inviteeId, "DRUM");

        decide(leaderId, id, "ACCEPTED").andExpect(status().isForbidden());
        decide(otherId, id, "DECLINED").andExpect(status().isForbidden());
        decide(inviteeId, id, "CANCELED").andExpect(status().isForbidden());
        decide(leaderId, id, "CANCELED").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED"));
    }

    @Test
    @DisplayName("이미 처리된 초대는 409 INVALID_STATE, 신청 id로 초대 경로를 부르면 404다")
    void decide_conflictAndDirection() throws Exception {
        long id = inviteToTeam(leaderId, inviteeId, "DRUM");
        decide(inviteeId, id, "DECLINED").andExpect(status().isOk());
        decide(inviteeId, id, "ACCEPTED").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        String json = send(post("/v1/join-requests"), otherId,
                "{\"targetType\":\"TEAM\",\"targetId\":" + teamId + ",\"instrument\":\"KEY\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long joinRequestId = ((Number) JsonPath.read(json, "$.data.id")).longValue();
        decide(leaderId, joinRequestId, "CANCELED").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOIN_REQUEST_NOT_FOUND"));
        send(patch("/v1/join-requests/" + id + "/status"), leaderId, "{\"status\":\"CANCELED\"}")
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("보낸 초대 목록은 팀장만 보고 대상이 필수다")
    void getSent() throws Exception {
        inviteToTeam(leaderId, inviteeId, "DRUM");
        inviteToTeam(leaderId, otherId, "KEY");

        send(get("/v1/invitations?targetType=TEAM&targetId=" + teamId), leaderId, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.content[0].invitee.id").value(otherId))
                .andExpect(jsonPath("$.data.content[0].inviter.id").value(leaderId));
        send(get("/v1/invitations?targetType=TEAM&targetId=" + teamId), otherId, null)
                .andExpect(status().isForbidden());
        send(get("/v1/invitations"), leaderId, null).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("받은 초대 목록은 내 초대만 보이고 대상·상태로 거를 수 있다")
    void getReceived() throws Exception {
        long teamInvitation = inviteToTeam(leaderId, inviteeId, "DRUM");
        send(post("/v1/invitations"), leaderId, inviteBody("GROUP", groupId, inviteeId, null))
                .andExpect(status().isCreated());
        inviteToTeam(leaderId, otherId, "KEY");
        decide(inviteeId, teamInvitation, "DECLINED").andExpect(status().isOk());

        send(get("/v1/users/me/invitations"), inviteeId, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)));
        send(get("/v1/users/me/invitations?targetType=GROUP&targetId=" + groupId), inviteeId, null)
                .andExpect(jsonPath("$.data.content", hasSize(1)));
        send(get("/v1/users/me/invitations?status=PENDING"), inviteeId, null)
                .andExpect(jsonPath("$.data.content", hasSize(1)));
        send(get("/v1/users/me/invitations?targetType=GROUP"), inviteeId, null)
                .andExpect(status().isBadRequest());
    }
}

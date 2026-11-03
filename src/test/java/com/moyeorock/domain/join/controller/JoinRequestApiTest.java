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
import org.springframework.transaction.annotation.Transactional;

/**
 * 가입 신청 흐름을 실제 HTTP·실제 DB로 확인한다. 클래스 단위 {@code @Transactional}이라 테스트마다 롤백되며,
 * 그래서 알림은 저장 여부가 아니라 발행된 이벤트로 확인한다(저장은 {@code JoinNotificationCommitTest}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
@RecordApplicationEvents
class JoinRequestApiTest {

    private long leaderId;    // 팀의 팀장이자 모임의 모임장
    private long applicantId;
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
        applicantId = saveUser("신청자").getId();
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

    private String bearer(long userId) {
        return "Bearer " + jwtProvider.generateToken(userId);
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
            long userId, String body) throws Exception {
        builder.header("Authorization", bearer(userId));
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(builder);
    }

    private String applyBody(String targetType, long targetId, String instrument, Long postId) {
        return """
                {"targetType":"%s","targetId":%d,"instrument":%s,"recruitPostId":%s,"message":"함께하고 싶어요"}
                """.formatted(targetType, targetId, instrument == null ? "null" : "\"" + instrument + "\"",
                postId == null ? "null" : postId);
    }

    private ResultActions applyToTeam(long userId, String instrument) throws Exception {
        return send(post("/v1/join-requests"), userId, applyBody("TEAM", teamId, instrument, null));
    }

    private long applyToTeamAndGetId(long userId, String instrument) throws Exception {
        String json = applyToTeam(userId, instrument).andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.data.id")).longValue();
    }

    private ResultActions changeStatus(long userId, long requestId, String status) throws Exception {
        return send(patch("/v1/join-requests/" + requestId + "/status"), userId,
                "{\"status\":\"" + status + "\"}");
    }

    private long createPost(String targetType, long targetId, String slotsJson) throws Exception {
        String json = send(post("/v1/recruit-post"), leaderId, """
                {"targetType":"%s","targetId":%d,"title":"공고","body":"본문","wantedSlots":%s,"region":"SEOUL"}
                """.formatted(targetType, targetId, slotsJson)).andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.data.id")).longValue();
    }

    private long createTeamPost() throws Exception {
        return createPost("TEAM", teamId, "[{\"instrument\":\"BASS\",\"count\":1}]");
    }

    @Test
    @DisplayName("팀에 신청하면 201과 PENDING 신청, 팀장에게 TEAM_APPLY 알림 이벤트가 간다")
    void apply_toTeam_returns201() throws Exception {
        applyToTeam(applicantId, "BASS")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.targetType").value("TEAM"))
                .andExpect(jsonPath("$.data.target.id").value(teamId))
                .andExpect(jsonPath("$.data.target.name").value("무명밴드"))
                .andExpect(jsonPath("$.data.target.region").value("SEOUL"))
                .andExpect(jsonPath("$.data.applicant.id").value(applicantId))
                .andExpect(jsonPath("$.data.instrument").value("BASS"))
                .andExpect(jsonPath("$.data.message").value("함께하고 싶어요"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.createdAt").exists());

        List<NotificationEvent> published = events.stream(NotificationEvent.class).toList();
        assertThat(published).hasSize(1);
        assertThat(published.get(0).userId()).isEqualTo(leaderId);
        assertThat(published.get(0).type()).isEqualTo(NotificationType.TEAM_APPLY);
    }

    @Test
    @DisplayName("모임에 신청하면 instrument를 저장하지 않고 모임장에게 알림이 간다")
    void apply_toGroup_ignoresInstrument() throws Exception {
        send(post("/v1/join-requests"), applicantId, applyBody("GROUP", groupId, "BASS", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.targetType").value("GROUP"))
                .andExpect(jsonPath("$.data.target.name").value("홍대동아리"))
                .andExpect(jsonPath("$.data.instrument").value(nullValue()));

        assertThat(events.stream(NotificationEvent.class).map(NotificationEvent::userId).toList())
                .containsExactly(leaderId);
    }

    @Test
    @DisplayName("팀 신청에 instrument가 없으면 400이다")
    void apply_toTeam_withoutInstrument_returns400() throws Exception {
        applyToTeam(applicantId, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("없는 팀·모임에 신청하면 404다")
    void apply_toMissingTarget_returns404() throws Exception {
        send(post("/v1/join-requests"), applicantId, applyBody("TEAM", 999_999, "BASS", null))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TEAM_NOT_FOUND"));
        send(post("/v1/join-requests"), applicantId, applyBody("GROUP", 999_999, null, null))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
    }

    @Test
    @DisplayName("같은 대상에 대기 중인 신청이 있으면 409 JOIN_REQUEST_DUPLICATED다")
    void apply_twice_returns409() throws Exception {
        applyToTeamAndGetId(applicantId, "BASS");

        applyToTeam(applicantId, "DRUM")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("JOIN_REQUEST_DUPLICATED"));
    }

    @Test
    @DisplayName("거절·취소된 뒤에는 다시 신청할 수 있다")
    void apply_again_afterRejected_returns201() throws Exception {
        long id = applyToTeamAndGetId(applicantId, "BASS");
        changeStatus(leaderId, id, "REJECTED").andExpect(status().isOk());

        applyToTeam(applicantId, "BASS").andExpect(status().isCreated());
    }

    @Test
    @DisplayName("초대가 대기 중인 대상에는 신청할 수 없다 — 중복 검사는 신청·초대를 가리지 않는다")
    void apply_whenInvitationPending_returns409() throws Exception {
        send(post("/v1/invitations"), leaderId,
                "{\"targetType\":\"TEAM\",\"targetId\":" + teamId + ",\"inviteeId\":" + applicantId
                        + ",\"instrument\":\"DRUM\"}").andExpect(status().isCreated());

        applyToTeam(applicantId, "DRUM")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("JOIN_REQUEST_DUPLICATED"));
    }

    @Test
    @DisplayName("자기 팀·모임의 팀장/모임장이 신청하면 400이다")
    void apply_asLeader_returns400() throws Exception {
        applyToTeam(leaderId, "BASS").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        send(post("/v1/join-requests"), leaderId, applyBody("GROUP", groupId, null, null))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("이미 소속된 팀원이 신청하면 409 ALREADY_MEMBER다")
    void apply_asMember_returns409() throws Exception {
        teamService.addMember(teamId, applicantId, Instrument.DRUM);

        applyToTeam(applicantId, "BASS")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_MEMBER"));
    }

    @Test
    @DisplayName("공고를 거친 신청: 열린 공고의 모집 세션이면 201이고 공고 상세에 반영된다")
    void apply_withRecruitPost_reflectsInPostDetail() throws Exception {
        long postId = createTeamPost();

        send(post("/v1/join-requests"), applicantId, applyBody("TEAM", teamId, "BASS", postId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.recruitPostId").value(postId));

        send(get("/v1/recruit-post/" + postId), applicantId, null)
                .andExpect(jsonPath("$.data.wantedSlots[0].appliedCount").value(1))
                .andExpect(jsonPath("$.data.myJoinRequest.status").value("PENDING"));
        send(get("/v1/recruit-post/" + postId), otherId, null)
                .andExpect(jsonPath("$.data.wantedSlots[0].appliedCount").value(1))
                .andExpect(jsonPath("$.data.myJoinRequest").value(nullValue()));
    }

    @Test
    @DisplayName("공고를 거친 신청의 검증: 없는 공고 404, 마감 409, 다른 대상·모집하지 않는 세션·내 공고 400")
    void apply_withRecruitPost_validations() throws Exception {
        long postId = createTeamPost();
        long otherTeamId = teamService.create(leaderId,
                new TeamCreateRequest("다른밴드", null, Region.SEOUL, List.of(Genre.POP), Instrument.EL_GT)).id();

        send(post("/v1/join-requests"), applicantId, applyBody("TEAM", teamId, "BASS", 999_999L))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RECRUIT_POST_NOT_FOUND"));
        send(post("/v1/join-requests"), applicantId, applyBody("TEAM", otherTeamId, "BASS", postId))
                .andExpect(status().isBadRequest());
        send(post("/v1/join-requests"), applicantId, applyBody("TEAM", teamId, "DRUM", postId))
                .andExpect(status().isBadRequest());

        send(patch("/v1/recruit-post/" + postId + "/status"), leaderId, "{\"status\":\"CLOSED\"}")
                .andExpect(status().isOk());
        send(post("/v1/join-requests"), applicantId, applyBody("TEAM", teamId, "BASS", postId))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    @DisplayName("내가 작성한 공고에는 신청할 수 없다")
    void apply_toOwnPost_returns400() throws Exception {
        // 작성자가 팀장이 아니게 된 경우를 흉내 낸다: 새 팀장에게 넘기고 원래 팀장이 일반 팀원으로 남는다.
        long postId = createTeamPost();
        teamService.addMember(teamId, applicantId, Instrument.DRUM);
        teamService.changeMember(leaderId, teamId, applicantId,
                new com.moyeorock.domain.team.dto.request.TeamMemberUpdateRequest(
                        com.moyeorock.domain.team.enums.TeamRole.LEADER, null));

        // 작성자는 이제 팀원이라 ALREADY_MEMBER가 먼저 걸린다. 팀을 나간 뒤에도 자기 공고 신청은 막혀야 한다.
        teamService.changeMemberStatus(leaderId, teamId, leaderId,
                new com.moyeorock.domain.team.dto.request.TeamMemberStatusUpdateRequest(
                        com.moyeorock.domain.team.enums.MemberStatus.LEFT));

        send(post("/v1/join-requests"), leaderId, applyBody("TEAM", teamId, "BASS", postId))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("승인하면 팀원이 되고 신청자에게 승인 알림이 간다")
    void approve_team_addsMember() throws Exception {
        long id = applyToTeamAndGetId(applicantId, "BASS");

        changeStatus(leaderId, id, "APPROVED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.decidedAt").exists())
                .andExpect(jsonPath("$.data.joined.targetType").value("TEAM"))
                .andExpect(jsonPath("$.data.joined.targetId").value(teamId))
                .andExpect(jsonPath("$.data.joined.role").value("MEMBER"))
                .andExpect(jsonPath("$.data.joined.instrument").value("BASS"));

        assertThat(teamMemberService.isActiveMember(teamId, applicantId)).isTrue();
        assertThat(events.stream(NotificationEvent.class)
                .filter(e -> e.type() == NotificationType.JOIN_REQUEST_APPROVED)
                .map(NotificationEvent::userId).toList()).containsExactly(applicantId);
    }

    @Test
    @DisplayName("모임 신청을 승인하면 모임원이 된다")
    void approve_group_addsMember() throws Exception {
        String json = send(post("/v1/join-requests"), applicantId, applyBody("GROUP", groupId, null, null))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(json, "$.data.id")).longValue();

        changeStatus(leaderId, id, "APPROVED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.joined.targetType").value("GROUP"))
                .andExpect(jsonPath("$.data.joined.role").value("MEMBER"));

        assertThat(groupMemberService.isActiveMember(groupId, applicantId)).isTrue();
    }

    @Test
    @DisplayName("거절하면 REJECTED 응답이고 팀원이 되지 않는다")
    void reject_returnsDecision() throws Exception {
        long id = applyToTeamAndGetId(applicantId, "BASS");

        changeStatus(leaderId, id, "REJECTED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.decidedAt").exists());

        assertThat(teamMemberService.isActiveMember(teamId, applicantId)).isFalse();
        assertThat(events.stream(NotificationEvent.class)
                .anyMatch(e -> e.type() == NotificationType.JOIN_REQUEST_REJECTED)).isTrue();
    }

    @Test
    @DisplayName("신청자 본인만 취소할 수 있고, 팀장이 취소하면 403이다")
    void cancel_onlyApplicant() throws Exception {
        long id = applyToTeamAndGetId(applicantId, "BASS");

        changeStatus(leaderId, id, "CANCELED").andExpect(status().isForbidden());
        changeStatus(applicantId, id, "CANCELED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED"));
    }

    @Test
    @DisplayName("승인·거절은 팀장만 할 수 있다 — 신청자 본인이나 남이 하면 403이다")
    void approve_byNonLeader_returns403() throws Exception {
        long id = applyToTeamAndGetId(applicantId, "BASS");

        changeStatus(applicantId, id, "APPROVED").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_TEAM_LEADER"));
        changeStatus(otherId, id, "REJECTED").andExpect(status().isForbidden());
        assertThat(teamMemberService.isActiveMember(teamId, applicantId)).isFalse();
    }

    @Test
    @DisplayName("이미 처리된 신청을 다시 처리하면 409 INVALID_STATE다")
    void decide_twice_returns409() throws Exception {
        long id = applyToTeamAndGetId(applicantId, "BASS");
        changeStatus(leaderId, id, "APPROVED").andExpect(status().isOk());

        changeStatus(leaderId, id, "REJECTED").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
        changeStatus(applicantId, id, "CANCELED").andExpect(status().isConflict());
    }

    @Test
    @DisplayName("PENDING으로 바꾸려 하면 400이고, 없는 신청은 404다")
    void updateStatus_invalid() throws Exception {
        long id = applyToTeamAndGetId(applicantId, "BASS");

        changeStatus(leaderId, id, "PENDING").andExpect(status().isBadRequest());
        changeStatus(leaderId, 999_999, "APPROVED").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOIN_REQUEST_NOT_FOUND"));
    }

    @Test
    @DisplayName("승인 시점에 이미 팀원이면 409 ALREADY_MEMBER이고 신청은 대기로 남는다")
    void approve_whenAlreadyMember_returns409() throws Exception {
        long id = applyToTeamAndGetId(applicantId, "BASS");
        teamService.addMember(teamId, applicantId, Instrument.DRUM);

        changeStatus(leaderId, id, "APPROVED").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_MEMBER"));
        send(get("/v1/users/me/join-requests"), applicantId, null)
                .andExpect(jsonPath("$.data.content[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("공고가 마감된 뒤에도 이미 들어온 대기 신청은 승인할 수 있다")
    void approve_afterPostClosed_succeeds() throws Exception {
        long postId = createTeamPost();
        String json = send(post("/v1/join-requests"), applicantId, applyBody("TEAM", teamId, "BASS", postId))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(json, "$.data.id")).longValue();
        send(patch("/v1/recruit-post/" + postId + "/status"), leaderId, "{\"status\":\"CLOSED\"}")
                .andExpect(status().isOk());

        changeStatus(leaderId, id, "APPROVED").andExpect(status().isOk());
    }

    @Test
    @DisplayName("받은 신청 목록은 팀장만 볼 수 있고 대상이 필수이며 상태로 거를 수 있다")
    void getReceived() throws Exception {
        long first = applyToTeamAndGetId(applicantId, "BASS");
        applyToTeamAndGetId(otherId, "DRUM");
        changeStatus(leaderId, first, "REJECTED");

        send(get("/v1/join-requests?targetType=TEAM&targetId=" + teamId), leaderId, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.content[0].applicant.id").value(otherId))   // 최신순
                .andExpect(jsonPath("$.data.totalElements").value(2));
        send(get("/v1/join-requests?targetType=TEAM&targetId=" + teamId + "&status=PENDING"), leaderId, null)
                .andExpect(jsonPath("$.data.content", hasSize(1)));

        send(get("/v1/join-requests?targetType=TEAM&targetId=" + teamId), otherId, null)
                .andExpect(status().isForbidden());
        send(get("/v1/join-requests"), leaderId, null).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("내 신청 목록은 내 신청만 최신순으로 보이고, 팀이 해체돼도 target에 id만 남는다")
    void getMine() throws Exception {
        applyToTeamAndGetId(applicantId, "BASS");
        send(post("/v1/join-requests"), applicantId, applyBody("GROUP", groupId, null, null))
                .andExpect(status().isCreated());
        applyToTeamAndGetId(otherId, "DRUM");

        send(get("/v1/users/me/join-requests"), applicantId, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.content[0].targetType").value("GROUP"))
                .andExpect(jsonPath("$.data.content[1].target.name").value("무명밴드"))
                .andExpect(jsonPath("$.data.content[0].applicant.id").value(applicantId));
        send(get("/v1/users/me/join-requests?status=APPROVED"), applicantId, null)
                .andExpect(jsonPath("$.data.content", hasSize(0)));
    }

    @Test
    @DisplayName("토큰 없이 호출하면 401이다")
    void withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/v1/join-requests").contentType(MediaType.APPLICATION_JSON)
                .content(applyBody("TEAM", teamId, "BASS", null))).andExpect(status().isUnauthorized());
    }
}

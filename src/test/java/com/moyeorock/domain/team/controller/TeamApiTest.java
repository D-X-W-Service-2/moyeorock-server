package com.moyeorock.domain.team.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.domain.team.entity.TeamMember;
import com.moyeorock.domain.team.enums.TeamRole;
import com.moyeorock.domain.team.repository.TeamMemberRepository;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.security.JwtProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보안 필터·JSON 바인딩·검증·에러 응답까지 실제 HTTP 흐름으로 확인한다. 테스트마다 롤백된다.
 * 가입 API는 3팀 소유라 팀원은 Repository로 직접 넣는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class TeamApiTest {

    private static final long LEADER_ID = 7L;
    private static final long MEMBER_ID = 8L;

    private static final String CREATE_BODY = """
            {"name":"무명밴드","description":"합주 위주로 편하게 합니다.","region":"SEOUL",
             "genres":["ROCK","INDIE"],"instrument":"BASS"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

    private String bearer(long userId) {
        return "Bearer " + jwtProvider.generateToken(userId);
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            long userId, String body) throws Exception {
        return mockMvc.perform(request.header("Authorization", bearer(userId))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long createTeam(long leaderId) throws Exception {
        MvcResult result = send(post("/v1/teams"), leaderId, CREATE_BODY).andExpect(status().isCreated()).andReturn();
        return ((Number) com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.data.id"))
                .longValue();
    }

    private void addMember(long teamId, long userId) {
        teamMemberRepository.saveAndFlush(TeamMember.create(teamId, userId, TeamRole.MEMBER, Instrument.DRUM));
    }

    @Test
    @DisplayName("토큰 없이 호출하면 401이다")
    void withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/v1/teams").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("팀을 만들면 201과 상세 응답을 돌려주고 요청자가 팀장이다")
    void create_returns201_withLeaderAsMyRole() throws Exception {
        send(post("/v1/teams"), LEADER_ID, CREATE_BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("무명밴드"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.genres[0]").value("ROCK"))
                .andExpect(jsonPath("$.data.memberCount").value(1))
                .andExpect(jsonPath("$.data.myRole").value("LEADER"))
                .andExpect(jsonPath("$.data.members[0].role").value("LEADER"))
                .andExpect(jsonPath("$.data.members[0].instrument").value("BASS"));
    }

    @Test
    @DisplayName("이름이 비었거나 세션이 없으면 400 VALIDATION_FAILED다")
    void create_withInvalidBody_returns400() throws Exception {
        send(post("/v1/teams"), LEADER_ID, "{\"name\":\"\",\"instrument\":\"BASS\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        send(post("/v1/teams"), LEADER_ID, "{\"name\":\"팀\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("팀장이 정보를 수정하면 200과 data null이고, 팀원이 하면 403이다")
    void update_byLeader_returns200_byMember_returns403() throws Exception {
        long teamId = createTeam(LEADER_ID);
        addMember(teamId, MEMBER_ID);
        String body = "{\"name\":\"유명밴드\",\"genres\":[\"PUNK\"]}";

        send(put("/v1/teams/" + teamId), LEADER_ID, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
        send(put("/v1/teams/" + teamId), MEMBER_ID, body)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_TEAM_LEADER"));
    }

    @Test
    @DisplayName("없는 팀은 404 TEAM_NOT_FOUND다")
    void update_unknownTeam_returns404() throws Exception {
        send(put("/v1/teams/999999"), LEADER_ID, "{\"name\":\"유명밴드\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEAM_NOT_FOUND"));
    }

    @Test
    @DisplayName("팀을 해체하면 200 DISBANDED이고, 다시 해체하면 409다")
    void disband_returns200_thenConflict() throws Exception {
        long teamId = createTeam(LEADER_ID);
        String body = "{\"status\":\"DISBANDED\"}";

        send(patch("/v1/teams/" + teamId + "/status"), LEADER_ID, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISBANDED"));
        send(patch("/v1/teams/" + teamId + "/status"), LEADER_ID, body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    @DisplayName("enum에 없는 값을 보내면 400이다")
    void disband_withUnknownEnumValue_returns400() throws Exception {
        long teamId = createTeam(LEADER_ID);

        send(patch("/v1/teams/" + teamId + "/status"), LEADER_ID, "{\"status\":\"GONE\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("팀장이 팀원에게 LEADER를 주면 위임되어 응답의 role이 LEADER다")
    void changeMember_delegation_returns200() throws Exception {
        long teamId = createTeam(LEADER_ID);
        addMember(teamId, MEMBER_ID);

        send(patch("/v1/teams/" + teamId + "/members/" + MEMBER_ID), LEADER_ID, "{\"role\":\"LEADER\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("LEADER"))
                .andExpect(jsonPath("$.data.instrument").value("DRUM"));
        // 위임 뒤에는 옛 팀장이 팀장이 아니라 같은 요청이 403이다
        send(patch("/v1/teams/" + teamId + "/members/" + MEMBER_ID), LEADER_ID, "{\"instrument\":\"BASS\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_TEAM_LEADER"));
    }

    @Test
    @DisplayName("팀장은 위임 전에 탈퇴할 수 없고(409), 팀원은 탈퇴할 수 있다(LEFT)")
    void leave_leaderConflict_memberOk() throws Exception {
        long teamId = createTeam(LEADER_ID);
        addMember(teamId, MEMBER_ID);
        String body = "{\"status\":\"LEFT\"}";

        send(patch("/v1/teams/" + teamId + "/members/" + LEADER_ID + "/status"), LEADER_ID, body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LEADER_MUST_DELEGATE_FIRST"));
        send(patch("/v1/teams/" + teamId + "/members/" + MEMBER_ID + "/status"), MEMBER_ID, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(MEMBER_ID))
                .andExpect(jsonPath("$.data.status").value("LEFT"));
    }

    @Test
    @DisplayName("팀장이 팀원을 강퇴하면 REMOVED이고, 팀원이 강퇴를 시도하면 403이다")
    void remove_byLeaderOk_byMemberForbidden() throws Exception {
        long teamId = createTeam(LEADER_ID);
        addMember(teamId, MEMBER_ID);
        String body = "{\"status\":\"REMOVED\"}";

        send(patch("/v1/teams/" + teamId + "/members/" + LEADER_ID + "/status"), MEMBER_ID, body)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_TEAM_LEADER"));
        send(patch("/v1/teams/" + teamId + "/members/" + MEMBER_ID + "/status"), LEADER_ID, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REMOVED"));
    }

    private ResultActions read(String url, long userId) throws Exception {
        return mockMvc.perform(get(url).header("Authorization", bearer(userId)));
    }

    @Test
    @DisplayName("팀 목록은 region·genre로 거르고 팀원 수와 장르를 채운다")
    void search_filtersAndFillsSummary() throws Exception {
        long teamId = createTeam(LEADER_ID);
        addMember(teamId, MEMBER_ID);

        read("/v1/teams?region=SEOUL&genre=ROCK", MEMBER_ID)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(teamId))
                .andExpect(jsonPath("$.data.content[0].memberCount").value(2))
                .andExpect(jsonPath("$.data.content[0].genres[0]").value("ROCK"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        read("/v1/teams?region=BUSAN", MEMBER_ID)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty());
    }

    @Test
    @DisplayName("해체된 팀은 목록에서 빠지고, status 파라미터를 보내도 마찬가지다")
    void search_excludesDisbandedTeams() throws Exception {
        long teamId = createTeam(LEADER_ID);
        send(patch("/v1/teams/" + teamId + "/status"), LEADER_ID, "{\"status\":\"DISBANDED\"}")
                .andExpect(status().isOk());

        read("/v1/teams", MEMBER_ID).andExpect(jsonPath("$.data.content").isEmpty());
        read("/v1/teams?status=DISBANDED", MEMBER_ID).andExpect(jsonPath("$.data.content").isEmpty());
    }

    @Test
    @DisplayName("팀 상세는 팀원 목록과 내 역할을 돌려주고, 없는 팀은 404다")
    void getDetail_returnsMembersAndMyRole_orNotFound() throws Exception {
        long teamId = createTeam(LEADER_ID);
        addMember(teamId, MEMBER_ID);

        read("/v1/teams/" + teamId, MEMBER_ID)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("무명밴드"))
                .andExpect(jsonPath("$.data.memberCount").value(2))
                .andExpect(jsonPath("$.data.myRole").value("MEMBER"))
                .andExpect(jsonPath("$.data.members[0].role").value("LEADER"));
        read("/v1/teams/" + teamId, 99L)
                .andExpect(jsonPath("$.data.myRole").value(org.hamcrest.Matchers.nullValue()));
        read("/v1/teams/999999", MEMBER_ID)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEAM_NOT_FOUND"));
    }

    @Test
    @DisplayName("해체된 팀의 상세와 팀원 목록은 404 TEAM_NOT_FOUND다")
    void disbandedTeam_detailAndMembers_returnNotFound() throws Exception {
        long teamId = createTeam(LEADER_ID);
        send(patch("/v1/teams/" + teamId + "/status"), LEADER_ID, "{\"status\":\"DISBANDED\"}")
                .andExpect(status().isOk());

        read("/v1/teams/" + teamId, LEADER_ID)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEAM_NOT_FOUND"));
        read("/v1/teams/" + teamId + "/members", LEADER_ID)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEAM_NOT_FOUND"));
    }

    @Test
    @DisplayName("팀원 목록은 탈퇴한 팀원을 뺀다")
    void getMembers_excludesLeftMembers() throws Exception {
        long teamId = createTeam(LEADER_ID);
        addMember(teamId, MEMBER_ID);
        send(patch("/v1/teams/" + teamId + "/members/" + MEMBER_ID + "/status"), MEMBER_ID, "{\"status\":\"LEFT\"}")
                .andExpect(status().isOk());

        read("/v1/teams/" + teamId + "/members", LEADER_ID)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.members.length()").value(1))
                .andExpect(jsonPath("$.data.members[0].role").value("LEADER"));
    }

    @Test
    @DisplayName("내 팀 목록은 내가 속한 팀만 돌려주고, 탈퇴하면 빠진다")
    void getMyTeams_returnsOnlyMyActiveTeams() throws Exception {
        long mine = createTeam(LEADER_ID);
        createTeam(MEMBER_ID);
        addMember(mine, MEMBER_ID);

        read("/v1/users/me/teams", MEMBER_ID)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
        send(patch("/v1/teams/" + mine + "/members/" + MEMBER_ID + "/status"), MEMBER_ID, "{\"status\":\"LEFT\"}")
                .andExpect(status().isOk());
        read("/v1/users/me/teams", MEMBER_ID)
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }
}

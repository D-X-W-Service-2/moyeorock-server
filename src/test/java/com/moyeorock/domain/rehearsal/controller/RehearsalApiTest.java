package com.moyeorock.domain.rehearsal.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.domain.team.entity.TeamMember;
import com.moyeorock.domain.team.enums.TeamRole;
import com.moyeorock.domain.team.repository.TeamMemberRepository;
import com.moyeorock.domain.user.entity.User;
import com.moyeorock.domain.user.repository.UserRepository;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.security.JwtProvider;
import java.time.LocalDateTime;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보안 필터·JSON 바인딩·검증·날짜 파라미터·에러 응답까지 실제 HTTP 흐름으로 확인한다. 테스트마다 롤백된다.
 * 팀 만들기는 team API로 하고, 팀원은 가입 API(3팀 소유)가 없어 Repository로 직접 넣는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class RehearsalApiTest {

    private static final long LEADER_ID = 7L;
    private static final long MEMBER_ID = 8L;
    private static final long STRANGER_ID = 9L;

    private static final String TEAM_BODY = """
            {"name":"무명밴드","region":"SEOUL","genres":["ROCK"],"instrument":"BASS"}
            """;

    private static final String REHEARSAL_BODY = """
            {"title":"1차 합주","startsAt":"2026-09-14T19:00:00","endsAt":"2026-09-14T21:00:00",
             "place":"홍대 사운드홀 2번방","memo":"베이스 앰프 없음"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

    @Autowired
    private UserRepository userRepository;

    private ResultActions send(MockHttpServletRequestBuilder request, long userId, String body) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + jwtProvider.generateToken(userId))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, long userId) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + jwtProvider.generateToken(userId)));
    }

    private long idOf(MvcResult result) throws Exception {
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data.id")).longValue();
    }

    // 작성자 요약(createdBy)은 users 행이 있어야 채워진다. 다른 테스트의 7·8번 같은 임의 id는 사용자 행이 없다.
    private User saveUser(String email, String nickname) {
        return userRepository.saveAndFlush(
                User.signupWithEmail(email, "hash", nickname, LocalDateTime.of(2026, 1, 1, 0, 0)));
    }

    private long createTeam(long leaderId) throws Exception {
        return idOf(send(post("/v1/teams"), leaderId, TEAM_BODY).andExpect(status().isCreated()).andReturn());
    }

    private void addMember(long teamId, long userId) {
        teamMemberRepository.saveAndFlush(TeamMember.create(teamId, userId, TeamRole.MEMBER, Instrument.DRUM));
    }

    private long createRehearsal(long teamId, long userId, String body) throws Exception {
        return idOf(send(post("/v1/teams/" + teamId + "/rehearsals"), userId, body)
                .andExpect(status().isCreated()).andReturn());
    }

    @Test
    @DisplayName("토큰 없이 호출하면 401이다")
    void withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/v1/rehearsals/1")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("팀원이 합주를 만들면 201과 상세 응답을 돌려주고 작성자는 수정할 수 있다")
    void create_returns201_withDetail() throws Exception {
        long teamId = createTeam(LEADER_ID);
        addMember(teamId, MEMBER_ID);

        send(post("/v1/teams/" + teamId + "/rehearsals"), MEMBER_ID, REHEARSAL_BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.team.id").value(teamId))
                .andExpect(jsonPath("$.data.team.name").value("무명밴드"))
                .andExpect(jsonPath("$.data.title").value("1차 합주"))
                .andExpect(jsonPath("$.data.startsAt").value("2026-09-14T19:00:00"))
                .andExpect(jsonPath("$.data.place").value("홍대 사운드홀 2번방"))
                .andExpect(jsonPath("$.data.canEdit").value(true));
    }

    @Test
    @DisplayName("제목이 비었거나 종료가 시작보다 앞서면 400 VALIDATION_FAILED다")
    void create_withInvalidBody_returns400() throws Exception {
        long teamId = createTeam(LEADER_ID);
        String blankTitle = """
                {"title":"","startsAt":"2026-09-14T19:00:00","endsAt":"2026-09-14T21:00:00"}
                """;
        String endBeforeStart = """
                {"title":"합주","startsAt":"2026-09-14T21:00:00","endsAt":"2026-09-14T19:00:00"}
                """;
        String noTimes = "{\"title\":\"합주\"}";

        for (String body : new String[] {blankTitle, endBeforeStart, noTimes}) {
            send(post("/v1/teams/" + teamId + "/rehearsals"), LEADER_ID, body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
    }

    @Test
    @DisplayName("팀원이 아니면 생성·목록·상세가 403이고, 없는 팀이면 404 TEAM_NOT_FOUND다")
    void nonMember_returns403_unknownTeam_returns404() throws Exception {
        long teamId = createTeam(LEADER_ID);
        long rehearsalId = createRehearsal(teamId, LEADER_ID, REHEARSAL_BODY);

        send(post("/v1/teams/" + teamId + "/rehearsals"), STRANGER_ID, REHEARSAL_BODY)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NO_PERMISSION"));
        send(get("/v1/teams/" + teamId + "/rehearsals").param("from", "2026-09-01").param("to", "2026-09-30"),
                STRANGER_ID).andExpect(status().isForbidden());
        send(get("/v1/rehearsals/" + rehearsalId), STRANGER_ID).andExpect(status().isForbidden());
        send(get("/v1/teams/999999/rehearsals").param("from", "2026-09-01").param("to", "2026-09-30"), LEADER_ID)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEAM_NOT_FOUND"));
    }

    @Test
    @DisplayName("팀 합주 목록: 기간 안의 합주만 시작 시각 순으로 돌려주고 팀 이름이 붙는다")
    void getTeamRehearsals_returnsOnlyPeriod_inOrder() throws Exception {
        long teamId = createTeam(LEADER_ID);
        createRehearsal(teamId, LEADER_ID, REHEARSAL_BODY.replace("1차 합주", "늦은 합주")
                .replace("2026-09-14T19", "2026-09-20T19").replace("2026-09-14T21", "2026-09-20T21"));
        createRehearsal(teamId, LEADER_ID, REHEARSAL_BODY.replace("1차 합주", "이른 합주"));
        createRehearsal(teamId, LEADER_ID, REHEARSAL_BODY.replace("1차 합주", "다른 달 합주")
                .replace("2026-09-14T19", "2026-10-14T19").replace("2026-09-14T21", "2026-10-14T21"));

        send(get("/v1/teams/" + teamId + "/rehearsals").param("from", "2026-09-01").param("to", "2026-09-30"),
                LEADER_ID)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rehearsals.length()").value(2))
                .andExpect(jsonPath("$.data.rehearsals[0].title").value("이른 합주"))
                .andExpect(jsonPath("$.data.rehearsals[1].title").value("늦은 합주"))
                .andExpect(jsonPath("$.data.rehearsals[0].teamName").value("무명밴드"))
                .andExpect(jsonPath("$.data.rehearsals[0].teamId").value(teamId));
    }

    @Test
    @DisplayName("팀 합주 목록: 날짜 형식이 틀리거나 하나만 보내거나 3달을 넘으면 400이고 둘 다 생략하면 200이다")
    void getTeamRehearsals_validatesPeriodParams() throws Exception {
        long teamId = createTeam(LEADER_ID);
        String path = "/v1/teams/" + teamId + "/rehearsals";

        send(get(path).param("from", "2026/09/01").param("to", "2026-09-30"), LEADER_ID)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        send(get(path).param("from", "2026-09-01"), LEADER_ID)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        send(get(path).param("from", "2026-09-01").param("to", "2026-12-02"), LEADER_ID)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        send(get(path), LEADER_ID)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.rehearsals").isArray());
    }

    @Test
    @DisplayName("합주 상세: 작성자와 팀장은 canEdit이 true이고 다른 팀원은 false다")
    void getDetail_canEdit_byRole() throws Exception {
        long teamId = createTeam(LEADER_ID);
        addMember(teamId, MEMBER_ID);
        addMember(teamId, 10L);
        long rehearsalId = createRehearsal(teamId, MEMBER_ID, REHEARSAL_BODY);

        send(get("/v1/rehearsals/" + rehearsalId), MEMBER_ID)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.canEdit").value(true));
        send(get("/v1/rehearsals/" + rehearsalId), LEADER_ID)
                .andExpect(jsonPath("$.data.canEdit").value(true));
        send(get("/v1/rehearsals/" + rehearsalId), 10L)
                .andExpect(jsonPath("$.data.canEdit").value(false));
        send(get("/v1/rehearsals/999999"), MEMBER_ID)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REHEARSAL_NOT_FOUND"));
    }

    @Test
    @DisplayName("합주 수정: 작성자와 팀장은 200으로 바꾸고 다른 팀원은 403이다")
    void update_byCreatorAndLeader_notByOtherMember() throws Exception {
        long teamId = createTeam(LEADER_ID);
        addMember(teamId, MEMBER_ID);
        addMember(teamId, 10L);
        long rehearsalId = createRehearsal(teamId, MEMBER_ID, REHEARSAL_BODY);
        String updated = REHEARSAL_BODY.replace("1차 합주", "수정된 합주");

        send(put("/v1/rehearsals/" + rehearsalId), MEMBER_ID, updated)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("수정된 합주"));
        send(put("/v1/rehearsals/" + rehearsalId), LEADER_ID, updated.replace("수정된", "팀장이 고친"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("팀장이 고친 합주"));
        send(put("/v1/rehearsals/" + rehearsalId), 10L, updated)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NO_PERMISSION"));
        send(put("/v1/rehearsals/" + rehearsalId), MEMBER_ID, "{\"title\":\"\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("합주 삭제: 200과 삭제한 id를 돌려주고 다시 조회하면 404이며, 다른 팀원은 403이다")
    void delete_returnsDeleteResponse_thenNotFound() throws Exception {
        long teamId = createTeam(LEADER_ID);
        addMember(teamId, MEMBER_ID);
        addMember(teamId, 10L);
        long rehearsalId = createRehearsal(teamId, MEMBER_ID, REHEARSAL_BODY);

        send(delete("/v1/rehearsals/" + rehearsalId), 10L).andExpect(status().isForbidden());
        send(delete("/v1/rehearsals/" + rehearsalId), LEADER_ID)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(rehearsalId))
                .andExpect(jsonPath("$.data.deleted").value(true));
        send(get("/v1/rehearsals/" + rehearsalId), LEADER_ID)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REHEARSAL_NOT_FOUND"));
    }

    @Test
    @DisplayName("내 합주 일정: 내가 속한 여러 팀의 합주를 모으고, 해체된 팀의 합주는 뺀다")
    void getMyRehearsals_mergesActiveTeams_excludesDisbanded() throws Exception {
        long teamA = createTeam(LEADER_ID);
        long teamB = createTeam(LEADER_ID);
        long disbanded = createTeam(LEADER_ID);
        createRehearsal(teamA, LEADER_ID, REHEARSAL_BODY.replace("1차 합주", "A팀 합주"));
        createRehearsal(teamB, LEADER_ID, REHEARSAL_BODY.replace("1차 합주", "B팀 합주")
                .replace("2026-09-14T19", "2026-09-15T19").replace("2026-09-14T21", "2026-09-15T21"));
        createRehearsal(disbanded, LEADER_ID, REHEARSAL_BODY.replace("1차 합주", "해체된 팀 합주"));
        send(patch("/v1/teams/" + disbanded + "/status"), LEADER_ID, "{\"status\":\"DISBANDED\"}")
                .andExpect(status().isOk());

        send(get("/v1/users/me/rehearsals").param("from", "2026-09-01").param("to", "2026-09-30"), LEADER_ID)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rehearsals.length()").value(2))
                .andExpect(jsonPath("$.data.rehearsals[0].title").value("A팀 합주"))
                .andExpect(jsonPath("$.data.rehearsals[1].title").value("B팀 합주"));
        send(get("/v1/users/me/rehearsals").param("from", "2026-09-01").param("to", "2026-09-30"), STRANGER_ID)
                .andExpect(jsonPath("$.data.rehearsals.length()").value(0));
    }

    @Test
    @DisplayName("실제 사용자가 만든 합주는 생성 응답과 다른 팀원의 상세 조회 모두 작성자 닉네임이 채워진다")
    void createdBy_isFilledWithRealUser() throws Exception {
        User author = saveUser("author@example.com", "작성자");
        User viewer = saveUser("viewer@example.com", "구경꾼");
        long teamId = createTeam(author.getId());
        addMember(teamId, viewer.getId());

        long rehearsalId = idOf(send(post("/v1/teams/" + teamId + "/rehearsals"), author.getId(), REHEARSAL_BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.createdBy.id").value(author.getId()))
                .andExpect(jsonPath("$.data.createdBy.nickname").value("작성자"))
                .andReturn());

        send(get("/v1/rehearsals/" + rehearsalId), viewer.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.createdBy.id").value(author.getId()))
                .andExpect(jsonPath("$.data.createdBy.nickname").value("작성자"))
                .andExpect(jsonPath("$.data.canEdit").value(false));
    }

    @Test
    @DisplayName("작성자가 탈퇴했으면 합주는 남고 createdBy만 null이다")
    void createdBy_isNull_whenAuthorWithdrew() throws Exception {
        User author = saveUser("gone@example.com", "탈퇴자");
        User leader = saveUser("leader@example.com", "팀장");
        long teamId = createTeam(leader.getId());
        addMember(teamId, author.getId());
        long rehearsalId = createRehearsal(teamId, author.getId(), REHEARSAL_BODY);
        author.withdraw();
        userRepository.saveAndFlush(author);

        send(get("/v1/rehearsals/" + rehearsalId), leader.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("1차 합주"))
                .andExpect(jsonPath("$.data.createdBy").doesNotExist());
    }
}

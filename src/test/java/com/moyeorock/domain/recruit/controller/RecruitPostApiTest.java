package com.moyeorock.domain.recruit.controller;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.domain.group.dto.request.GroupCreateRequest;
import com.moyeorock.domain.group.enums.GroupType;
import com.moyeorock.domain.group.service.GroupService;
import com.moyeorock.domain.team.dto.request.TeamCreateRequest;
import com.moyeorock.domain.team.dto.request.TeamMemberUpdateRequest;
import com.moyeorock.domain.team.dto.request.TeamStatusUpdateRequest;
import com.moyeorock.domain.team.enums.TeamRole;
import com.moyeorock.domain.team.enums.TeamStatus;
import com.moyeorock.domain.team.service.TeamService;
import com.moyeorock.domain.user.entity.User;
import com.moyeorock.domain.user.repository.UserRepository;
import com.moyeorock.domain.user.service.UserWithdrawService;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Region;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
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
 * 보안 필터 체인·JSON 직렬화·쿼리 파라미터 바인딩·에러 응답까지 실제 HTTP 흐름으로 확인한다.
 * 클래스 단위 {@code @Transactional}이라 테스트마다 롤백돼 목록 조회가 다른 테스트 데이터에 오염되지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class RecruitPostApiTest {

    private static final String BASE = "/v1/recruit-post";

    private long authorId;   // 팀의 팀장이자 모임의 모임장 (공고 작성 권한자)
    private long otherId;    // 팀·모임과 무관한 사용자
    private long teamId;
    private long groupId;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserWithdrawService userWithdrawService;

    @Autowired
    private TeamService teamService;

    @Autowired
    private GroupService groupService;

    @BeforeEach
    void setUp() {
        authorId = saveUser("리더").getId();
        otherId = saveUser("남").getId();
        teamId = teamService.create(authorId,
                new TeamCreateRequest("무명밴드", null, Region.SEOUL, List.of(Genre.ROCK), Instrument.BASS)).id();
        groupId = groupService.create(authorId,
                new GroupCreateRequest("홍대동아리", null, GroupType.REGULAR, Region.SEOUL, null)).id();
    }

    private User saveUser(String nickname) {
        return userRepository.saveAndFlush(User.signupWithEmail(
                nickname + "@example.com", "hash", nickname, LocalDateTime.now()));
    }

    private String teamBody() {
        return """
                {"targetType":"TEAM","targetId":%d,"title":"베이스 1명 구합니다","body":"주 1회 홍대에서 합주합니다.",
                 "wantedSlots":[{"instrument":"BASS","count":1},{"instrument":"KEY","count":1}],"region":"SEOUL"}
                """.formatted(teamId);
    }

    private String groupBody() {
        return """
                {"targetType":"GROUP","targetId":%d,"title":"모임 드러머","body":"b",
                 "wantedSlots":[{"instrument":"DRUM","count":1}],"region":"BUSAN"}
                """.formatted(groupId);
    }

    private String bearer(long userId) {
        return "Bearer " + jwtProvider.generateToken(userId);
    }

    private ResultActions createPost(long userId, String body) throws Exception {
        return mockMvc.perform(post(BASE).header("Authorization", bearer(userId))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long createPostAndGetId(long userId, String body) throws Exception {
        MvcResult result = createPost(userId, body).andExpect(status().isCreated()).andReturn();
        String json = result.getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(json, "$.data.id")).longValue();
    }

    @Test
    @DisplayName("토큰 없이 호출하면 401이다")
    void withoutToken_returns401() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("공고를 작성하면 201과 상세 응답(canEdit=true, OPEN, updatedAt 포함)을 돌려준다")
    void create_returns201_withDetail() throws Exception {
        createPost(authorId, teamBody())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.targetType").value("TEAM"))
                .andExpect(jsonPath("$.data.target.id").value(teamId))
                .andExpect(jsonPath("$.data.target.name").value("무명밴드"))
                .andExpect(jsonPath("$.data.target.region").value("SEOUL"))
                .andExpect(jsonPath("$.data.target.genres[0]").value("ROCK"))
                .andExpect(jsonPath("$.data.author.id").value(authorId))
                .andExpect(jsonPath("$.data.author.nickname").value("리더"))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.canEdit").value(true))
                .andExpect(jsonPath("$.data.wantedSlots[0].instrument").value("BASS"))
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.updatedAt").exists());
    }

    @Test
    @DisplayName("신청이 없는 새 공고는 세션별 appliedCount가 0이고 myJoinRequest는 null이다")
    void create_returnsZeroAppliedCount_andNullMyJoinRequest() throws Exception {
        createPost(authorId, teamBody())
                .andExpect(jsonPath("$.data.wantedSlots[0].appliedCount").value(0))
                .andExpect(jsonPath("$.data.wantedSlots[1].appliedCount").value(0))
                .andExpect(jsonPath("$.data", hasKey("myJoinRequest")))
                .andExpect(jsonPath("$.data.myJoinRequest").value(nullValue()));
    }

    @Test
    @DisplayName("모집 슬롯이 비어 있으면 400 VALIDATION_FAILED다")
    void create_withEmptyWantedSlots_returns400() throws Exception {
        createPost(authorId, """
                {"targetType":"TEAM","targetId":%d,"title":"t","body":"b","wantedSlots":[],"region":"SEOUL"}
                """.formatted(teamId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("작성·수정 모두 같은 직군이 두 번 오면 400 VALIDATION_FAILED다")
    void duplicateInstrumentInWantedSlots_returns400() throws Exception {
        createPost(authorId, """
                {"targetType":"TEAM","targetId":%d,"title":"t","body":"b",
                 "wantedSlots":[{"instrument":"BASS","count":1},{"instrument":"BASS","count":2}],"region":"SEOUL"}
                """.formatted(teamId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        long id = createPostAndGetId(authorId, teamBody());
        mockMvc.perform(put(BASE + "/" + id).header("Authorization", bearer(authorId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"t","body":"b","region":"SEOUL",
                                 "wantedSlots":[{"instrument":"DRUM","count":1},{"instrument":"DRUM","count":1}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("존재하지 않는 악기 값(폐지된 ETC 포함)은 400이다")
    void create_withUnknownInstrument_returns400() throws Exception {
        createPost(authorId, """
                {"targetType":"TEAM","targetId":%d,"title":"t","body":"b",
                 "wantedSlots":[{"instrument":"ETC","count":1}],"region":"SEOUL"}
                """.formatted(teamId))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("상세 조회: 현재 팀장은 canEdit=true, 다른 사용자는 false다")
    void getDetail_canEditDependsOnViewer() throws Exception {
        long id = createPostAndGetId(authorId, teamBody());

        mockMvc.perform(get(BASE + "/" + id).header("Authorization", bearer(authorId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.canEdit").value(true));
        mockMvc.perform(get(BASE + "/" + id).header("Authorization", bearer(otherId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.canEdit").value(false));
    }

    @Test
    @DisplayName("없는 공고 조회는 404 RECRUIT_POST_NOT_FOUND다")
    void getDetail_notFound_returns404() throws Exception {
        mockMvc.perform(get(BASE + "/999999").header("Authorization", bearer(authorId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECRUIT_POST_NOT_FOUND"));
    }

    @Test
    @DisplayName("목록: 쿼리 파라미터(targetType·instrument·status)로 걸러지고 페이지 응답 모양을 따른다")
    void search_filtersByQueryParams() throws Exception {
        createPostAndGetId(authorId, teamBody());
        createPostAndGetId(authorId, groupBody());

        mockMvc.perform(get(BASE).header("Authorization", bearer(otherId))
                        .param("targetType", "GROUP").param("instrument", "DRUM").param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("모임 드러머"))
                .andExpect(jsonPath("$.data.content[0].targetType").value("GROUP"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.last").value(true));
    }

    @Test
    @DisplayName("목록: 잘못된 enum 쿼리 파라미터는 500이 아니라 400이다")
    void search_withInvalidEnumParam_returns400() throws Exception {
        mockMvc.perform(get(BASE).header("Authorization", bearer(otherId)).param("instrument", "NOPE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("목록: 임의 sort 파라미터가 와도 500이 아니라 최신순으로 정상 응답한다")
    void search_withArbitrarySortParam_stillOk() throws Exception {
        createPostAndGetId(authorId, teamBody());

        mockMvc.perform(get(BASE).header("Authorization", bearer(otherId)).param("sort", "noSuchProperty,asc"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("수정: 팀장은 200과 갱신된 상세를, 팀장이 아닌 사용자는 403 NOT_TEAM_LEADER를 받는다")
    void update_leaderOk_otherForbidden() throws Exception {
        long id = createPostAndGetId(authorId, teamBody());
        String updateBody = """
                {"title":"수정된 제목","body":"수정된 본문","wantedSlots":[{"instrument":"DRUM","count":2}],"region":"BUSAN"}
                """;

        mockMvc.perform(put(BASE + "/" + id).header("Authorization", bearer(authorId))
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("수정된 제목"))
                .andExpect(jsonPath("$.data.region").value("BUSAN"))
                .andExpect(jsonPath("$.data.target.id").value(teamId));
        mockMvc.perform(put(BASE + "/" + id).header("Authorization", bearer(otherId))
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_TEAM_LEADER"));
    }

    @Test
    @DisplayName("수정: 마감된 공고는 작성자도 409 INVALID_STATE를 받고 내용은 그대로다")
    void update_closedPost_returns409() throws Exception {
        long id = createPostAndGetId(authorId, teamBody());
        mockMvc.perform(patch(BASE + "/" + id + "/status").header("Authorization", bearer(authorId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(put(BASE + "/" + id).header("Authorization", bearer(authorId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"수정 시도","body":"b","wantedSlots":[{"instrument":"DRUM","count":1}],"region":"BUSAN"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        mockMvc.perform(get(BASE + "/" + id).header("Authorization", bearer(authorId)))
                .andExpect(jsonPath("$.data.title").value(org.hamcrest.Matchers.not("수정 시도")));
    }

    @Test
    @DisplayName("마감: 팀장이 CLOSED로 바꾸면 200, 같은 요청을 또 하면 409 INVALID_STATE다")
    void close_thenCloseAgain_returns409() throws Exception {
        long id = createPostAndGetId(authorId, teamBody());
        String closeBody = "{\"status\":\"CLOSED\"}";

        mockMvc.perform(patch(BASE + "/" + id + "/status").header("Authorization", bearer(authorId))
                        .contentType(MediaType.APPLICATION_JSON).content(closeBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.status").value("CLOSED"));
        mockMvc.perform(patch(BASE + "/" + id + "/status").header("Authorization", bearer(authorId))
                        .contentType(MediaType.APPLICATION_JSON).content(closeBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    @DisplayName("작성: 팀장이 아닌 사용자는 403 NOT_TEAM_LEADER, 없는 팀은 404 TEAM_NOT_FOUND이고 공고는 만들어지지 않는다")
    void create_notLeaderOrMissingTeam() throws Exception {
        createPost(otherId, teamBody()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_TEAM_LEADER"));
        createPost(authorId, teamBody().replace("\"targetId\":" + teamId, "\"targetId\":999999"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TEAM_NOT_FOUND"));

        mockMvc.perform(get(BASE).header("Authorization", bearer(authorId)))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("모임 공고: 모임장은 작성·수정할 수 있고(target은 이름만, canEdit=true), 다른 사용자는 403 NOT_GROUP_OWNER다")
    void groupPost_ownerOnly() throws Exception {
        createPost(otherId, groupBody()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_GROUP_OWNER"));
        long id = createPostAndGetId(authorId, groupBody());

        mockMvc.perform(get(BASE + "/" + id).header("Authorization", bearer(authorId)))
                .andExpect(jsonPath("$.data.targetType").value("GROUP"))
                .andExpect(jsonPath("$.data.target.name").value("홍대동아리"))
                .andExpect(jsonPath("$.data.canEdit").value(true));
        mockMvc.perform(put(BASE + "/" + id).header("Authorization", bearer(otherId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"body\":\"b\",\"wantedSlots\":[{\"instrument\":\"DRUM\",\"count\":1}],\"region\":\"BUSAN\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_GROUP_OWNER"));
    }

    @Test
    @DisplayName("목록: 항목마다 팀 요약(이름·지역·장르)이 채워진다")
    void list_fillsTeamTarget() throws Exception {
        createPostAndGetId(authorId, teamBody());

        mockMvc.perform(get(BASE).header("Authorization", bearer(otherId)))
                .andExpect(jsonPath("$.data.content[0].target.name").value("무명밴드"))
                .andExpect(jsonPath("$.data.content[0].target.genres[0]").value("ROCK"));
    }

    @Test
    @DisplayName("팀이 해체되면 공고는 남고 target은 id만, 수정·마감은 404 TEAM_NOT_FOUND다")
    void disbandedTeam_keepsPostWithIdOnlyTarget() throws Exception {
        long id = createPostAndGetId(authorId, teamBody());
        teamService.disband(authorId, teamId, new TeamStatusUpdateRequest(TeamStatus.DISBANDED));

        mockMvc.perform(get(BASE + "/" + id).header("Authorization", bearer(authorId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.target.id").value(teamId))
                .andExpect(jsonPath("$.data.target.name").doesNotExist())
                .andExpect(jsonPath("$.data.canEdit").value(false));
        mockMvc.perform(patch(BASE + "/" + id + "/status").header("Authorization", bearer(authorId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TEAM_NOT_FOUND"));
    }

    @Test
    @DisplayName("탈퇴한 작성자는 author가 null이다")
    void withdrawnAuthor_isNull() throws Exception {
        // 직책(팀장·모임장)을 가진 채로는 탈퇴할 수 없게 막을 예정이라(1팀), 직책이 없는 작성자로 만든다.
        // 공고는 팀장만 쓰므로 쓴 뒤에 팀장을 넘긴다 — 작성자는 현재 팀장이 아니어도 공고의 작성자로 남는다.
        long writerId = saveUser("작성자").getId();
        long writerTeamId = teamService.create(writerId,
                new TeamCreateRequest("작성자밴드", null, Region.SEOUL, List.of(Genre.ROCK), Instrument.BASS)).id();
        teamService.addMember(writerTeamId, otherId, Instrument.DRUM);
        long id = createPostAndGetId(writerId, """
                {"targetType":"TEAM","targetId":%d,"title":"t","body":"b",
                 "wantedSlots":[{"instrument":"KEY","count":1}],"region":"SEOUL"}
                """.formatted(writerTeamId));
        teamService.changeMember(writerId, writerTeamId, otherId, new TeamMemberUpdateRequest(TeamRole.LEADER, null));
        userWithdrawService.withdraw(writerId);

        mockMvc.perform(get(BASE + "/" + id).header("Authorization", bearer(otherId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasKey("author")))
                .andExpect(jsonPath("$.data.author").value(nullValue()));
    }
}

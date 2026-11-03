package com.moyeorock.domain.performance.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.domain.group.entity.GroupMember;
import com.moyeorock.domain.group.enums.GroupRole;
import com.moyeorock.domain.group.repository.GroupMemberRepository;
import com.moyeorock.domain.performance.enums.PerformanceStatus;
import com.moyeorock.domain.performance.repository.PerformanceRepository;
import com.moyeorock.domain.user.entity.User;
import com.moyeorock.domain.user.repository.UserRepository;
import com.moyeorock.global.security.JwtProvider;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보안 필터 체인·요청 검증·JSON 직렬화·에러 응답까지 실제 HTTP 흐름으로 확인한다.
 * 클래스 단위 {@code @Transactional}이라 테스트마다 롤백된다. 모임원 가입 API(3팀)가 아직 없어
 * 모임원은 Repository로 직접 넣는다. 공연 상태 변경 API는 이 PR에 없어서 지난 공연은 Repository로 상태를 바꿔 만든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class PerformanceApiTest {

    private static final long NON_MEMBER_ID = 999_999L;

    // 생성·수정은 지난 일시를 막아서 테스트 일시는 실행 날짜 기준으로 잡는다 — 고정 날짜는 그날이 지나면 테스트가 깨진다.
    private static final String PERFORMED_AT = iso(LocalDate.now().plusMonths(2).atTime(18, 0));
    private static final String EARLIER = iso(LocalDate.now().plusMonths(1).atTime(18, 0));
    private static final String RESCHEDULED = iso(LocalDate.now().plusMonths(2).plusWeeks(1).atTime(18, 0));
    private static final String PAST = iso(LocalDate.now().minusMonths(1).atTime(18, 0));

    private static final String CREATE_BODY = """
            {"title":"2026 정기공연","description":"1년에 한 번 하는 정기공연입니다.",
             "performedAt":"%s","venue":"홍대 롤링홀","posterImage":"https://cdn/poster.jpg"}
            """.formatted(PERFORMED_AT);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Autowired
    private PerformanceRepository performanceRepository;

    private long ownerId;
    private long memberId;
    private long groupId;

    @BeforeEach
    void setUp() throws Exception {
        ownerId = saveUser("모임장");
        memberId = saveUser("모임원");
        groupId = createGroup(ownerId);
        groupMemberRepository.save(GroupMember.create(groupId, memberId, GroupRole.MEMBER));
    }

    @Test
    @DisplayName("토큰 없이 호출하면 401이다")
    void withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/v1/groups/" + groupId + "/performances")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("모임장이 공연을 만들면 201과 상세 응답(PLANNED, 모임 정보, 만든 사람, canEdit=true)을 돌려준다")
    void create_returns201_withDetail() throws Exception {
        createPerformance(ownerId, CREATE_BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("2026 정기공연"))
                .andExpect(jsonPath("$.data.performedAt").value(PERFORMED_AT))
                .andExpect(jsonPath("$.data.venue").value("홍대 롤링홀"))
                .andExpect(jsonPath("$.data.status").value("PLANNED"))
                .andExpect(jsonPath("$.data.group.id").value(groupId))
                .andExpect(jsonPath("$.data.group.name").value("홍대 밴드부"))
                .andExpect(jsonPath("$.data.createdBy.id").value(ownerId))
                .andExpect(jsonPath("$.data.createdBy.nickname").value("모임장"))
                .andExpect(jsonPath("$.data.canEdit").value(true))
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.teams").doesNotExist());
    }

    @Test
    @DisplayName("제목·장소·일시가 빠지면 400 VALIDATION_FAILED다")
    void create_withMissingRequiredFields_returns400() throws Exception {
        createPerformance(ownerId, """
                {"description":"설명만 있다"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("지난 일시로 공연을 만들면 400 VALIDATION_FAILED다")
    void create_withPastPerformedAt_returns400() throws Exception {
        createPerformance(ownerId, CREATE_BODY.replace(PERFORMED_AT, PAST))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("모임장이 아닌 모임원이 공연을 만들면 403 NOT_GROUP_OWNER다")
    void create_byMember_returns403() throws Exception {
        createPerformance(memberId, CREATE_BODY)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_GROUP_OWNER"));
    }

    @Test
    @DisplayName("모임 공연 목록은 공연일 늦은 순으로 페이징되고, 엔티티에 없는 sort를 보내도 500이 아니다")
    void getList_pagesLatestFirst_andIgnoresClientSort() throws Exception {
        createPerformanceAndGetId(ownerId, CREATE_BODY.replace(PERFORMED_AT, EARLIER)
                .replace("2026 정기공연", "봄 공연"));
        createPerformanceAndGetId(ownerId, CREATE_BODY.replace("2026 정기공연", "가을 공연"));

        mockMvc.perform(get("/v1/groups/" + groupId + "/performances?page=0&size=1&sort=noSuchField")
                        .header("Authorization", bearer(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("가을 공연"))
                .andExpect(jsonPath("$.data.content[0].createdAt").exists())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.last").value(false));
    }

    @Test
    @DisplayName("동아리 홈의 upcomingPerformances는 공연이 없으면 빈 배열이고, 있으면 PLANNED·RECRUITING을 가까운 순으로 최대 3건 준다")
    void groupHome_upcomingPerformances() throws Exception {
        mockMvc.perform(get("/v1/groups/" + groupId).header("Authorization", bearer(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.upcomingPerformances").isArray())
                .andExpect(jsonPath("$.data.upcomingPerformances").isEmpty());

        createPerformanceAndGetId(ownerId, CREATE_BODY.replace("2026 정기공연", "두 달 뒤 공연"));
        createPerformanceAndGetId(ownerId, CREATE_BODY.replace(PERFORMED_AT, EARLIER).replace("2026 정기공연", "한 달 뒤 공연"));
        createPerformanceAndGetId(ownerId, CREATE_BODY.replace(PERFORMED_AT, RESCHEDULED).replace("2026 정기공연", "더 뒤 공연"));
        createPerformanceAndGetId(ownerId, CREATE_BODY.replace(PERFORMED_AT, RESCHEDULED).replace("2026 정기공연", "가장 뒤 공연"));
        long doneId = createPerformanceAndGetId(ownerId, CREATE_BODY.replace(PERFORMED_AT, EARLIER).replace("2026 정기공연", "끝난 공연"));
        performanceRepository.findById(doneId).orElseThrow().changeStatus(PerformanceStatus.DONE);

        mockMvc.perform(get("/v1/groups/" + groupId).header("Authorization", bearer(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.upcomingPerformances.length()").value(3))
                .andExpect(jsonPath("$.data.upcomingPerformances[0].title").value("한 달 뒤 공연"))
                .andExpect(jsonPath("$.data.upcomingPerformances[1].title").value("두 달 뒤 공연"))
                .andExpect(jsonPath("$.data.upcomingPerformances[2].title").value("더 뒤 공연"))
                .andExpect(jsonPath("$.data.upcomingPerformances[0].posterImage").value("https://cdn/poster.jpg"))
                .andExpect(jsonPath("$.data.upcomingPerformances[0].status").value("PLANNED"));
    }

    @Test
    @DisplayName("period=UPCOMING은 PLANNED·RECRUITING 공연을 가까운 순으로, PAST는 DONE·CANCELED 공연을 주고 status는 실제 상태다")
    void getList_filtersByPeriod() throws Exception {
        createPerformanceAndGetId(ownerId, CREATE_BODY.replace("2026 정기공연", "겨울 공연"));
        createPerformanceAndGetId(ownerId, CREATE_BODY.replace(PERFORMED_AT, EARLIER).replace("2026 정기공연", "가을 공연"));
        long doneId = createPerformanceAndGetId(ownerId, CREATE_BODY.replace("2026 정기공연", "끝난 공연"));
        performanceRepository.findById(doneId).orElseThrow().changeStatus(PerformanceStatus.DONE);

        getList(memberId, "UPCOMING")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].title").value("가을 공연"))
                .andExpect(jsonPath("$.data.content[0].status").value("PLANNED"))
                .andExpect(jsonPath("$.data.content[1].title").value("겨울 공연"));
        getList(memberId, "PAST")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("끝난 공연"))
                .andExpect(jsonPath("$.data.content[0].status").value("DONE"));
    }

    @Test
    @DisplayName("없는 period 값(노션의 ONGOING 등)이면 400 VALIDATION_FAILED다")
    void getList_withInvalidPeriod_returns400() throws Exception {
        getList(memberId, "ONGOING")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("모임원이 아니면 공연 목록은 403 NOT_GROUP_MEMBER다")
    void getList_byNonMember_returns403() throws Exception {
        mockMvc.perform(get("/v1/groups/" + groupId + "/performances").header("Authorization", bearer(NON_MEMBER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_GROUP_MEMBER"));
    }

    @Test
    @DisplayName("일반 모임원이 보는 공연 상세는 canEdit=false다")
    void getDetail_byMember_returnsCanEditFalse() throws Exception {
        long performanceId = createPerformanceAndGetId(ownerId, CREATE_BODY);

        getDetail(memberId, performanceId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(performanceId))
                .andExpect(jsonPath("$.data.canEdit").value(false));
    }

    @Test
    @DisplayName("모임원이 아니면 공연 상세는 403 NOT_GROUP_MEMBER다")
    void getDetail_byNonMember_returns403() throws Exception {
        long performanceId = createPerformanceAndGetId(ownerId, CREATE_BODY);

        getDetail(NON_MEMBER_ID, performanceId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_GROUP_MEMBER"));
    }

    @Test
    @DisplayName("없는 공연이면 404 PERFORMANCE_NOT_FOUND다")
    void getDetail_missing_returns404() throws Exception {
        getDetail(memberId, 987_654L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PERFORMANCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("모임이 해체되면 그 모임의 공연 상세는 404 GROUP_NOT_FOUND다")
    void getDetail_ofDisbandedGroup_returns404() throws Exception {
        long performanceId = createPerformanceAndGetId(ownerId, CREATE_BODY);
        mockMvc.perform(patch("/v1/groups/" + groupId + "/status").header("Authorization", bearer(ownerId)))
                .andExpect(status().isOk());

        getDetail(memberId, performanceId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
    }

    @Test
    @DisplayName("모임장이 공연을 고치면 200과 바뀐 상세 응답을 돌려준다")
    void update_byOwner_returnsUpdatedDetail() throws Exception {
        long performanceId = createPerformanceAndGetId(ownerId, CREATE_BODY);

        updatePerformance(ownerId, performanceId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("2026 정기공연 (연기)"))
                .andExpect(jsonPath("$.data.venue").value("신촌 클럽"))
                .andExpect(jsonPath("$.data.posterImage").value(nullValue()))
                .andExpect(jsonPath("$.data.canEdit").value(true));
    }

    @Test
    @DisplayName("모임장이 아닌 모임원이 공연을 고치면 403 NOT_GROUP_OWNER다")
    void update_byMember_returns403() throws Exception {
        long performanceId = createPerformanceAndGetId(ownerId, CREATE_BODY);

        updatePerformance(memberId, performanceId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_GROUP_OWNER"));
    }

    @Test
    @DisplayName("공연 일시를 지난 시각으로 고치면 400 VALIDATION_FAILED다")
    void update_toPastPerformedAt_returns400() throws Exception {
        long performanceId = createPerformanceAndGetId(ownerId, CREATE_BODY);

        updatePerformance(ownerId, performanceId, """
                {"title":"2026 정기공연","performedAt":"%s","venue":"홍대 롤링홀"}
                """.formatted(PAST))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // 응답 JSON의 일시 형식(초까지)과 맞춘다 — LocalDateTime.toString()은 0초를 생략한다.
    private static String iso(LocalDateTime dateTime) {
        return dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private String bearer(long userId) {
        return "Bearer " + jwtProvider.generateToken(userId);
    }

    private long saveUser(String nickname) {
        return userRepository.save(User.signupWithEmail(
                nickname + "@moyeorock.test", "hash", nickname, LocalDateTime.now())).getId();
    }

    private long createGroup(long userId) throws Exception {
        String json = mockMvc.perform(post("/v1/groups").header("Authorization", bearer(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"홍대 밴드부","type":"REGULAR","region":"SEOUL","coverImage":"https://cdn/cover.jpg"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.data.id")).longValue();
    }

    private ResultActions createPerformance(long userId, String body) throws Exception {
        return mockMvc.perform(post("/v1/groups/" + groupId + "/performances").header("Authorization", bearer(userId))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long createPerformanceAndGetId(long userId, String body) throws Exception {
        String json = createPerformance(userId, body).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.data.id")).longValue();
    }

    private ResultActions getList(long userId, String period) throws Exception {
        return mockMvc.perform(get("/v1/groups/" + groupId + "/performances?period=" + period)
                .header("Authorization", bearer(userId)));
    }

    private ResultActions getDetail(long userId, long performanceId) throws Exception {
        return mockMvc.perform(get("/v1/performances/" + performanceId).header("Authorization", bearer(userId)));
    }

    private ResultActions updatePerformance(long userId, long performanceId) throws Exception {
        return updatePerformance(userId, performanceId, """
                {"title":"2026 정기공연 (연기)","performedAt":"%s","venue":"신촌 클럽"}
                """.formatted(RESCHEDULED));
    }

    private ResultActions updatePerformance(long userId, long performanceId, String body) throws Exception {
        return mockMvc.perform(put("/v1/performances/" + performanceId).header("Authorization", bearer(userId))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}

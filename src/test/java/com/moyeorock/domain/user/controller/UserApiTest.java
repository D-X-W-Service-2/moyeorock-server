package com.moyeorock.domain.user.controller;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.domain.group.dto.request.GroupCreateRequest;
import com.moyeorock.domain.group.enums.GroupType;
import com.moyeorock.domain.group.service.GroupService;
import com.moyeorock.domain.team.dto.request.TeamCreateRequest;
import com.moyeorock.domain.team.dto.request.TeamStatusUpdateRequest;
import com.moyeorock.domain.team.enums.TeamStatus;
import com.moyeorock.domain.team.service.TeamService;
import com.moyeorock.domain.user.entity.User;
import com.moyeorock.domain.user.repository.UserRepository;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Region;
import com.moyeorock.global.security.JwtProvider;
import java.time.LocalDateTime;
import java.util.List;
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
 * user Phase 1 7개 + Phase 2 내 활동·타인 프로필을 실제 HTTP 흐름(보안 필터·@Valid·쿼리 파라미터·에러 봉투)으로 확인한다.
 * 사용자는 Repository로 바로 만들고 토큰은 {@code JwtProvider}로 발급한다(RecruitPostApiTest 선례).
 * 팀 활동 재료는 2팀 {@code TeamService.create}로 만든다. 남의 Repository·Entity는 테스트에서도 만지지 않는다.
 * 클래스 단위 {@code @Transactional}이라 테스트마다 롤백된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class UserApiTest {

    private static final String BASE = "/v1/users";
    private static final String ONBOARDING_BODY = """
            {"nickname":"서준","region":"SEOUL","genres":["ROCK","JAZZ"],
             "instruments":[{"instrument":"BASS","level":"NOVICE"},{"instrument":"KEY","level":"ADVANCED"}]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeamService teamService;

    @Autowired
    private GroupService groupService;

    private long saveUser(String email, String nickname) {
        return userRepository.saveAndFlush(
                User.signupWithEmail(email, "hash", nickname, LocalDateTime.of(2026, 9, 29, 10, 0))).getId();
    }

    /** userId가 팀장(LEADER)·BASS로 들어간 팀 하나를 만들고 팀 id를 돌려준다. 남의 Repository는 테스트에서도 만지지 않는다. */
    private long createTeam(long userId, String name) {
        return teamService.create(userId,
                new TeamCreateRequest(name, null, Region.SEOUL, List.of(Genre.ROCK), Instrument.BASS)).id();
    }

    private String bearer(long userId) {
        return "Bearer " + jwtProvider.generateToken(userId);
    }

    private ResultActions putJson(String path, long userId, String body) throws Exception {
        return mockMvc.perform(put(BASE + path).header("Authorization", bearer(userId))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions onboard(long userId, String body) throws Exception {
        return mockMvc.perform(post(BASE + "/me/onboarding").header("Authorization", bearer(userId))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    @DisplayName("users 경로는 전부 로그인 필수라 토큰 없이 부르면 401이다")
    void withoutToken_returns401() throws Exception {
        mockMvc.perform(get(BASE + "/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/search").param("nickname", "서")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/nickname/check").param("nickname", "서준")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/me/activities")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("내 활동은 200이며 속한 팀이 없으면 세 목록 모두 빈 배열이다(null 아님)")
    void getActivities_noTeams_returnsEmptyLists() throws Exception {
        long me = saveUser("me@example.com", "조회자");

        mockMvc.perform(get(BASE + "/me/activities").header("Authorization", bearer(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.teams", hasSize(0)))
                .andExpect(jsonPath("$.data.groups", hasSize(0)))
                .andExpect(jsonPath("$.data.performances", hasSize(0)));
    }

    @Test
    @DisplayName("내 활동: 내가 만든 팀이 팀 요약 + 내 역할(LEADER)·악기로 담긴다. groups·performances는 4팀 메서드 전이라 빈 배열이다")
    void getActivities_withTeam_returnsMembership() throws Exception {
        long me = saveUser("me@example.com", "조회자");
        long teamId = createTeam(me, "무명밴드");

        mockMvc.perform(get(BASE + "/me/activities").header("Authorization", bearer(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teams", hasSize(1)))
                .andExpect(jsonPath("$.data.teams[0].team.id").value(teamId))
                .andExpect(jsonPath("$.data.teams[0].team.name").value("무명밴드"))
                .andExpect(jsonPath("$.data.teams[0].team.region").value("SEOUL"))
                .andExpect(jsonPath("$.data.teams[0].team.memberCount").value(1))
                .andExpect(jsonPath("$.data.teams[0].role").value("LEADER"))
                .andExpect(jsonPath("$.data.teams[0].instrument").value("BASS"))
                .andExpect(jsonPath("$.data.groups", hasSize(0)))
                .andExpect(jsonPath("$.data.performances", hasSize(0)));
    }

    @Test
    @DisplayName("내 활동: 토큰의 사용자가 없으면(탈퇴 등) 다른 /me와 같이 404 USER_NOT_FOUND다")
    void getActivities_unknownUser_returns404() throws Exception {
        mockMvc.perform(get(BASE + "/me/activities").header("Authorization", bearer(999_999L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("내 프로필 조회는 200이고 본인 전용 필드(email·isRecommendable·isActivityPublic)를 담는다")
    void getMe_returnsOwnFields() throws Exception {
        long id = saveUser("seojun@example.com", "임시서준");

        mockMvc.perform(get(BASE + "/me").header("Authorization", bearer(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.email").value("seojun@example.com"))
                .andExpect(jsonPath("$.data.loginType").value("EMAIL"))
                .andExpect(jsonPath("$.data.isRecommendable").value(true))
                .andExpect(jsonPath("$.data.isActivityPublic").value(true))
                .andExpect(jsonPath("$.data.onboardingCompleted").value(false))
                .andExpect(jsonPath("$.data.instruments", hasSize(0)))
                .andExpect(jsonPath("$.data", hasKey("region")))
                .andExpect(jsonPath("$.data.region").value(nullValue()));
    }

    @Test
    @DisplayName("토큰의 사용자가 없으면(탈퇴 등) 404 USER_NOT_FOUND다")
    void getMe_unknownUser_returns404() throws Exception {
        mockMvc.perform(get(BASE + "/me").header("Authorization", bearer(999_999L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("온보딩 등록은 200이며 닉네임·지역·장르·세션이 반영되고 onboardingCompleted=true가 된다. 재호출도 200(멱등)")
    void onboarding_returns200_andIsIdempotent() throws Exception {
        long id = saveUser("seojun@example.com", "임시서준");

        onboard(id, ONBOARDING_BODY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("서준"))
                .andExpect(jsonPath("$.data.region").value("SEOUL"))
                .andExpect(jsonPath("$.data.genres[0]").value("ROCK"))
                .andExpect(jsonPath("$.data.instruments", hasSize(2)))
                .andExpect(jsonPath("$.data.instruments[0].instrument").value("BASS"))
                .andExpect(jsonPath("$.data.onboardingCompleted").value(true));
        onboard(id, ONBOARDING_BODY).andExpect(status().isOk());
    }

    @Test
    @DisplayName("온보딩: 세션이 비거나 같은 악기가 두 번 오거나 폐지된 ETC면 400이다")
    void onboarding_invalidInstruments_returns400() throws Exception {
        long id = saveUser("seojun@example.com", "임시서준");

        onboard(id, "{\"nickname\":\"서준\",\"region\":\"SEOUL\",\"genres\":[],\"instruments\":[]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        onboard(id, """
                {"nickname":"서준","region":"SEOUL","genres":[],
                 "instruments":[{"instrument":"BASS","level":"NOVICE"},{"instrument":"BASS","level":"ADVANCED"}]}
                """)
                .andExpect(status().isBadRequest());
        onboard(id, """
                {"nickname":"서준","region":"SEOUL","genres":[],"instruments":[{"instrument":"ETC","level":"NOVICE"}]}
                """)
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("온보딩: 다른 ACTIVE 회원이 쓰는 닉네임이면 409 NICKNAME_DUPLICATED다")
    void onboarding_duplicateNickname_returns409() throws Exception {
        saveUser("other@example.com", "서준");
        long id = saveUser("seojun@example.com", "임시서준");

        onboard(id, ONBOARDING_BODY)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NICKNAME_DUPLICATED"));
    }

    @Test
    @DisplayName("프로필 수정은 전체 교체라 뺀 선택 필드는 null이 되고, 본인 닉네임 그대로는 중복으로 보지 않는다")
    void updateMe_replacesAll() throws Exception {
        long id = saveUser("seojun@example.com", "임시서준");
        onboard(id, ONBOARDING_BODY).andExpect(status().isOk());

        putJson("/me", id, """
                {"nickname":"서준","bio":"베이스 칩니다","isRecommendable":false,"isActivityPublic":false}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("서준"))
                .andExpect(jsonPath("$.data.bio").value("베이스 칩니다"))
                .andExpect(jsonPath("$.data.region").value(nullValue()))
                .andExpect(jsonPath("$.data.isRecommendable").value(false))
                .andExpect(jsonPath("$.data.isActivityPublic").value(false))
                .andExpect(jsonPath("$.data.instruments", hasSize(2)));
    }

    @Test
    @DisplayName("프로필 수정: 필수 필드(isRecommendable·isActivityPublic)가 빠지면 400이다")
    void updateMe_missingRequired_returns400() throws Exception {
        long id = saveUser("seojun@example.com", "임시서준");

        putJson("/me", id, "{\"nickname\":\"서준\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("세션 수정은 전체 교체이며 응답은 instruments 래퍼다")
    void updateInstruments_replacesAll() throws Exception {
        long id = saveUser("seojun@example.com", "임시서준");
        onboard(id, ONBOARDING_BODY).andExpect(status().isOk());

        putJson("/me/instruments", id, "{\"instruments\":[{\"instrument\":\"DRUM\",\"level\":\"BEGINNER\"}]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.instruments", hasSize(1)))
                .andExpect(jsonPath("$.data.instruments[0].instrument").value("DRUM"))
                .andExpect(jsonPath("$.data.instruments[0].level").value("BEGINNER"));
        putJson("/me/instruments", id, "{\"instruments\":[]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("탈퇴는 200과 withdrawnAt을 돌려주고, 같은 토큰으로 다시 부르면 404다")
    void withdraw_thenTokenIsUseless() throws Exception {
        long id = saveUser("seojun@example.com", "임시서준");

        mockMvc.perform(delete(BASE + "/me").header("Authorization", bearer(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.withdrawnAt").exists());
        mockMvc.perform(get(BASE + "/me").header("Authorization", bearer(id)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("탈퇴: 팀장인 활동 중인 팀이 있으면 409 WITHDRAW_BLOCKED_BY_ROLE이고 메시지에 팀 이름이 담기며 계정은 그대로다")
    void withdraw_blockedWhileLeadingTeam() throws Exception {
        long id = saveUser("seojun@example.com", "임시서준");
        createTeam(id, "무명밴드");

        mockMvc.perform(delete(BASE + "/me").header("Authorization", bearer(id)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("WITHDRAW_BLOCKED_BY_ROLE"))
                .andExpect(jsonPath("$.message").value("팀 '무명밴드'의 팀장을 위임한 뒤 탈퇴할 수 있습니다."))
                .andExpect(jsonPath("$.data").value(nullValue()));
        mockMvc.perform(get(BASE + "/me").header("Authorization", bearer(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("seojun@example.com"));
    }

    @Test
    @DisplayName("탈퇴: 팀장이던 팀을 해체하면 더는 막히지 않고 200이다")
    void withdraw_allowedAfterDisbandingLedTeam() throws Exception {
        long id = saveUser("seojun@example.com", "임시서준");
        long teamId = createTeam(id, "무명밴드");
        teamService.disband(id, teamId, new TeamStatusUpdateRequest(TeamStatus.DISBANDED));

        mockMvc.perform(delete(BASE + "/me").header("Authorization", bearer(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.withdrawnAt").exists());
    }

    @Test
    @DisplayName("탈퇴: 모임장인 활동 중인 모임이 있으면 409이고 메시지에 모임 이름이 담기며, 모임을 해체하면 200이다")
    void withdraw_blockedWhileOwningGroup_thenAllowedAfterDisband() throws Exception {
        long id = saveUser("seojun@example.com", "임시서준");
        long groupId = groupService.create(id,
                new GroupCreateRequest("홍대 밴드부", null, GroupType.REGULAR, Region.SEOUL, null)).id();

        mockMvc.perform(delete(BASE + "/me").header("Authorization", bearer(id)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WITHDRAW_BLOCKED_BY_ROLE"))
                .andExpect(jsonPath("$.message").value("모임 '홍대 밴드부'의 모임장을 위임하거나 모임을 해체한 뒤 탈퇴할 수 있습니다."));

        groupService.disband(id, groupId);
        mockMvc.perform(delete(BASE + "/me").header("Authorization", bearer(id)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("검색: 부분 일치 ACTIVE 회원만 페이지 응답으로, 검색어가 없거나 비면 400이다")
    void search_partialMatch_orBlank400() throws Exception {
        long me = saveUser("me@example.com", "검색자");
        saveUser("a@example.com", "서준이");
        saveUser("b@example.com", "민서");
        long withdrawn = saveUser("c@example.com", "서준탈퇴");
        mockMvc.perform(delete(BASE + "/me").header("Authorization", bearer(withdrawn))).andExpect(status().isOk());

        mockMvc.perform(get(BASE + "/search").header("Authorization", bearer(me)).param("nickname", "서준"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].nickname").value("서준이"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.last").value(true));
        // 탈퇴하면 닉네임이 "탈퇴회원_{id}"로 바뀌므로, 그 접두사로 검색해도 0건이어야 ACTIVE 필터가 실제로 걸린 것이다
        mockMvc.perform(get(BASE + "/search").header("Authorization", bearer(me)).param("nickname", "탈퇴회원"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(get(BASE + "/search").header("Authorization", bearer(me)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(get(BASE + "/search").header("Authorization", bearer(me)).param("nickname", "  "))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("검색: 임의 sort 파라미터가 와도 500이 아니라 닉네임순으로 정상 응답한다")
    void search_withArbitrarySortParam_stillOk() throws Exception {
        long me = saveUser("me@example.com", "검색자");

        mockMvc.perform(get(BASE + "/search").header("Authorization", bearer(me))
                        .param("nickname", "검").param("sort", "noSuchProperty,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("닉네임 중복 확인: 정규화된 닉네임과 available을 돌려주고, 파라미터가 없으면 400이다")
    void checkNickname_returnsAvailability() throws Exception {
        long me = saveUser("me@example.com", "검색자");
        saveUser("a@example.com", "서준");

        mockMvc.perform(get(BASE + "/nickname/check").header("Authorization", bearer(me)).param("nickname", " 서준 "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("서준"))
                .andExpect(jsonPath("$.data.available").value(false));
        mockMvc.perform(get(BASE + "/nickname/check").header("Authorization", bearer(me)).param("nickname", "새닉네임"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true));
        mockMvc.perform(get(BASE + "/nickname/check").header("Authorization", bearer(me)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("타인 프로필 조회는 200이며 본인 전용 필드(email·isRecommendable)가 없고, 세션에 id가 없으며, 공개 사용자는 activities에 팀 활동이 담긴다")
    void getProfile_returnsPublicFieldsOnly() throws Exception {
        long me = saveUser("me@example.com", "조회자");
        long target = saveUser("minseo@example.com", "임시민서");
        onboard(target, ONBOARDING_BODY).andExpect(status().isOk());
        long teamId = createTeam(target, "무명밴드");

        mockMvc.perform(get(BASE + "/" + target).header("Authorization", bearer(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(target))
                .andExpect(jsonPath("$.data.nickname").value("서준"))
                .andExpect(jsonPath("$.data.region").value("SEOUL"))
                .andExpect(jsonPath("$.data.genres[0]").value("ROCK"))
                .andExpect(jsonPath("$.data.instruments", hasSize(2)))
                .andExpect(jsonPath("$.data.instruments[0].instrument").value("BASS"))
                .andExpect(jsonPath("$.data.instruments[0].level").value("NOVICE"))
                .andExpect(jsonPath("$.data.instruments[0]", not(hasKey("id"))))
                .andExpect(jsonPath("$.data.isActivityPublic").value(true))
                .andExpect(jsonPath("$.data.activities.teams", hasSize(1)))
                .andExpect(jsonPath("$.data.activities.teams[0].team.id").value(teamId))
                .andExpect(jsonPath("$.data.activities.teams[0].role").value("LEADER"))
                .andExpect(jsonPath("$.data.activities.groups", hasSize(0)))
                .andExpect(jsonPath("$.data.activities.performances", hasSize(0)))
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data", not(hasKey("email"))))
                .andExpect(jsonPath("$.data", not(hasKey("isRecommendable"))))
                .andExpect(jsonPath("$.data", not(hasKey("loginType"))))
                .andExpect(jsonPath("$.data", not(hasKey("onboardingCompleted"))));
    }

    @Test
    @DisplayName("타인 프로필: 활동 비공개로 바꾼 사용자는 팀이 있어도 isActivityPublic=false, activities=null로 내려간다(빈 목록 아님)")
    void getProfile_privateActivity() throws Exception {
        long me = saveUser("me@example.com", "조회자");
        long target = saveUser("minseo@example.com", "민서");
        createTeam(target, "비공개밴드");
        putJson("/me", target, "{\"nickname\":\"민서\",\"isRecommendable\":true,\"isActivityPublic\":false}")
                .andExpect(status().isOk());

        mockMvc.perform(get(BASE + "/" + target).header("Authorization", bearer(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isActivityPublic").value(false))
                .andExpect(jsonPath("$.data", hasKey("activities")))
                .andExpect(jsonPath("$.data.activities").value(nullValue()));
    }

    @Test
    @DisplayName("타인 프로필: 본인 id로 불러도 특별 처리 없이 타인 프로필 형태(email 없음)로 200이다")
    void getProfile_ownId_sameShape() throws Exception {
        long me = saveUser("me@example.com", "조회자");

        mockMvc.perform(get(BASE + "/" + me).header("Authorization", bearer(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(me))
                .andExpect(jsonPath("$.data.nickname").value("조회자"))
                .andExpect(jsonPath("$.data", not(hasKey("email"))));
    }

    @Test
    @DisplayName("타인 프로필: 탈퇴한 사용자와 없는 id는 404 USER_NOT_FOUND다")
    void getProfile_withdrawnOrUnknown_returns404() throws Exception {
        long me = saveUser("me@example.com", "조회자");
        long withdrawn = saveUser("bye@example.com", "탈퇴예정");
        mockMvc.perform(delete(BASE + "/me").header("Authorization", bearer(withdrawn))).andExpect(status().isOk());

        mockMvc.perform(get(BASE + "/" + withdrawn).header("Authorization", bearer(me)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        mockMvc.perform(get(BASE + "/999999").header("Authorization", bearer(me)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("타인 프로필: id가 숫자가 아니면 400이고, /me는 /{id}에 먹히지 않고 내 프로필로 간다")
    void getProfile_nonNumericId_returns400_andMeStillRoutes() throws Exception {
        long me = saveUser("me@example.com", "조회자");

        mockMvc.perform(get(BASE + "/abc").header("Authorization", bearer(me)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(get(BASE + "/me").header("Authorization", bearer(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("me@example.com"));
    }
}

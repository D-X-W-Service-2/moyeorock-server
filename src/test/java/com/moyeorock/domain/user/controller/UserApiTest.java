package com.moyeorock.domain.user.controller;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.domain.user.entity.User;
import com.moyeorock.domain.user.repository.UserRepository;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * user Phase 1 7개를 실제 HTTP 흐름(보안 필터·@Valid·쿼리 파라미터·에러 봉투)으로 확인한다.
 * 사용자는 Repository로 바로 만들고 토큰은 {@code JwtProvider}로 발급한다(RecruitPostApiTest 선례).
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

    private long saveUser(String email, String nickname) {
        return userRepository.saveAndFlush(
                User.signupWithEmail(email, "hash", nickname, LocalDateTime.of(2026, 9, 29, 10, 0))).getId();
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
}

package com.moyeorock.domain.auth.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.domain.auth.client.KakaoOAuthClient;
import com.moyeorock.domain.auth.client.KakaoUserInfo;
import com.moyeorock.domain.auth.service.KakaoSignupTokenProvider;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가입·로그인 4개를 실제 HTTP 흐름(보안 필터·@Valid·에러 봉투)으로 확인한다.
 * 카카오 외부 호출만 {@code KakaoOAuthClient}를 Mockito 빈으로 바꿔 끊는다(실제 HTTP 스텁은 KakaoOAuthClientTest).
 * 클래스 단위 {@code @Transactional}이라 가입한 사용자는 테스트마다 롤백된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class AuthApiTest {

    private static final String BASE = "/v1/auth";
    private static final String SIGNUP_BODY = """
            {"email":"seojun@example.com","password":"Passw0rd!","name":"서준","privacyAgreed":true}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KakaoSignupTokenProvider signupTokenProvider;

    @MockitoBean
    private KakaoOAuthClient kakaoOAuthClient;

    private ResultActions postJson(String path, String body) throws Exception {
        return mockMvc.perform(post(BASE + path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    @DisplayName("auth 경로는 토큰 없이 통과하고, 잘못된 본문이면 401이 아니라 400 VALIDATION_FAILED다")
    void authPaths_arePublic_validationReturns400() throws Exception {
        postJson("/signup", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    @DisplayName("이메일 가입은 201과 토큰·userId·임시 닉네임·onboardingCompleted=false를 돌려준다")
    void signup_returns201_withToken() throws Exception {
        postJson("/signup", SIGNUP_BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.userId").isNumber())
                .andExpect(jsonPath("$.data.nickname").value("서준"))
                .andExpect(jsonPath("$.data.onboardingCompleted").value(false));
    }

    @Test
    @DisplayName("비밀번호 규칙 위반(특수문자 없음)·약관 미동의는 @Valid로 400이다")
    void signup_invalidPasswordOrNoConsent_returns400() throws Exception {
        postJson("/signup", """
                {"email":"a@example.com","password":"password1","name":"a","privacyAgreed":true}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        postJson("/signup", """
                {"email":"a@example.com","password":"Passw0rd!","name":"a","privacyAgreed":false}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("같은 이메일로 다시 가입하면 409 EMAIL_DUPLICATED다")
    void signup_duplicateEmail_returns409() throws Exception {
        postJson("/signup", SIGNUP_BODY).andExpect(status().isCreated());
        postJson("/signup", SIGNUP_BODY)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_DUPLICATED"));
    }

    @Test
    @DisplayName("가입한 토큰으로 /v1/users/me를 부르면 200이다 (발급 토큰이 필터를 통과한다)")
    void signupToken_worksOnProtectedPath() throws Exception {
        String json = postJson("/signup", SIGNUP_BODY).andReturn().getResponse().getContentAsString();
        String token = com.jayway.jsonpath.JsonPath.read(json, "$.data.accessToken");

        mockMvc.perform(get("/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("seojun@example.com"));
    }

    @Test
    @DisplayName("로그인은 200, 비밀번호가 틀리거나 없는 이메일이면 같은 401 LOGIN_FAILED다")
    void login_okOrSame401() throws Exception {
        postJson("/signup", SIGNUP_BODY).andExpect(status().isCreated());

        postJson("/login", "{\"email\":\"seojun@example.com\",\"password\":\"Passw0rd!\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.nickname").value("서준"));
        postJson("/login", "{\"email\":\"seojun@example.com\",\"password\":\"wrong-pass\"}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
        postJson("/login", "{\"email\":\"nobody@example.com\",\"password\":\"Passw0rd!\"}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
    }

    @Test
    @DisplayName("로그인은 비밀번호 규칙을 검사하지 않고 빈 값만 400이다")
    void login_blankPassword_returns400() throws Exception {
        postJson("/login", "{\"email\":\"seojun@example.com\",\"password\":\"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("카카오 인증: 신규면 isNewUser=true와 signupToken만, code가 없으면 400이다")
    void kakaoAuth_newUser_returnsSignupToken() throws Exception {
        given(kakaoOAuthClient.fetchUser(anyString())).willReturn(new KakaoUserInfo("kakao-123", "민서"));

        mockMvc.perform(post(BASE + "/kakao").param("code", "auth-code"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isNewUser").value(true))
                .andExpect(jsonPath("$.data.signupToken").isString())
                .andExpect(jsonPath("$.data.kakaoNickname").value("민서"))
                .andExpect(jsonPath("$.data.auth").value(nullValue()));
        mockMvc.perform(post(BASE + "/kakao"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("카카오 인증 실패는 400 OAUTH_FAILED다")
    void kakaoAuth_oauthFailure_returns400() throws Exception {
        given(kakaoOAuthClient.fetchUser(anyString())).willThrow(new BusinessException(ErrorCode.OAUTH_FAILED));

        mockMvc.perform(post(BASE + "/kakao").param("code", "bad-code"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OAUTH_FAILED"));
    }

    @Test
    @DisplayName("카카오 가입 완료: 201로 계정이 생기고, 같은 토큰을 다시 쓰면 409, 이후 카카오 인증은 isNewUser=false다")
    void kakaoSignup_thenLogin() throws Exception {
        String signupToken = signupTokenProvider.issue("kakao-123", "민서");
        String body = "{\"signupToken\":\"" + signupToken + "\",\"privacyAgreed\":true}";

        postJson("/kakao/signup", body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.nickname").value("민서"))
                .andExpect(jsonPath("$.data.onboardingCompleted").value(false));
        postJson("/kakao/signup", body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_REGISTERED"));

        given(kakaoOAuthClient.fetchUser(anyString())).willReturn(new KakaoUserInfo("kakao-123", "민서"));
        mockMvc.perform(post(BASE + "/kakao").param("code", "auth-code"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isNewUser").value(false))
                .andExpect(jsonPath("$.data.auth.accessToken").isString())
                .andExpect(jsonPath("$.data.signupToken").value(nullValue()));
    }

    @Test
    @DisplayName("카카오 가입 완료: 위조 토큰은 401 INVALID_SIGNUP_TOKEN, 약관 미동의는 400이다")
    void kakaoSignup_invalidTokenOrNoConsent() throws Exception {
        postJson("/kakao/signup", "{\"signupToken\":\"not-a-token\",\"privacyAgreed\":true}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_SIGNUP_TOKEN"));
        postJson("/kakao/signup", "{\"signupToken\":\"x\",\"privacyAgreed\":false}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}

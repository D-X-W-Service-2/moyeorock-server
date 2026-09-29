package com.moyeorock.domain.auth.controller;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moyeorock.domain.auth.dto.response.AuthTokenResponse;
import com.moyeorock.domain.auth.dto.response.KakaoAuthResponse;
import com.moyeorock.domain.auth.service.AuthService;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    private static final AuthTokenResponse TOKEN = new AuthTokenResponse("access", 12L, "서준", false);
    private static final String SIGNUP_JSON =
            "{\"email\":\"seojun@example.com\",\"password\":\"Passw0rd!\",\"name\":\"서준\",\"privacyAgreed\":true}";

    @Autowired
    MockMvc mockMvc;
    @MockitoBean
    AuthService authService;

    @Test
    @DisplayName("POST /v1/auth/signup — 201, AuthTokenResponse")
    void signup_returns201() throws Exception {
        given(authService.signup(any())).willReturn(TOKEN);

        mockMvc.perform(post("/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(SIGNUP_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access"))
                .andExpect(jsonPath("$.data.userId").value(12))
                .andExpect(jsonPath("$.data.onboardingCompleted").value(false));
    }

    @Test
    @DisplayName("POST /v1/auth/signup — 이메일 형식·비밀번호 규칙·동의 false는 400, fieldErrors에 필드명")
    void signup_invalid_returns400() throws Exception {
        mockMvc.perform(post("/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"short\",\"name\":\"서준\",\"privacyAgreed\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fieldErrors[*].field", hasItem("email")))
                .andExpect(jsonPath("$.error.fieldErrors[*].field", hasItem("password")))
                .andExpect(jsonPath("$.error.fieldErrors[*].field", hasItem("privacyAgreed")));
    }

    @Test
    @DisplayName("POST /v1/auth/signup — 특수문자 없는 비밀번호는 400 (영문·숫자·특수문자 각 1자 이상)")
    void signup_passwordWithoutSpecial_returns400() throws Exception {
        mockMvc.perform(post("/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\",\"password\":\"Password12\",\"name\":\"서준\",\"privacyAgreed\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[*].field", containsInAnyOrder("password")));
    }

    @Test
    @DisplayName("POST /v1/auth/signup — 이메일 중복은 409 EMAIL_DUPLICATED")
    void signup_duplicated_returns409() throws Exception {
        given(authService.signup(any())).willThrow(new BusinessException(ErrorCode.EMAIL_DUPLICATED));

        mockMvc.perform(post("/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(SIGNUP_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_DUPLICATED"));
    }

    @Test
    @DisplayName("POST /v1/auth/login — 200")
    void login_returns200() throws Exception {
        given(authService.login(any())).willReturn(TOKEN);

        mockMvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"seojun@example.com\",\"password\":\"anything\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("서준"));
    }

    @Test
    @DisplayName("POST /v1/auth/login — 실패는 401 LOGIN_FAILED, 빈 비밀번호는 400")
    void login_failed_returns401_and_blank_returns400() throws Exception {
        given(authService.login(any())).willThrow(new BusinessException(ErrorCode.LOGIN_FAILED));
        mockMvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"seojun@example.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("LOGIN_FAILED"));

        mockMvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"seojun@example.com\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("password"));
    }

    @Test
    @DisplayName("POST /v1/auth/kakao — 기존 회원 200 isNewUser=false + auth")
    void kakao_existing_returns200() throws Exception {
        given(authService.kakaoAuth("abc")).willReturn(KakaoAuthResponse.loggedIn(TOKEN));

        mockMvc.perform(post("/v1/auth/kakao").param("code", "abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isNewUser").value(false))
                .andExpect(jsonPath("$.data.auth.accessToken").value("access"));
    }

    @Test
    @DisplayName("POST /v1/auth/kakao — 신규 200 isNewUser=true + signupToken")
    void kakao_new_returns200() throws Exception {
        given(authService.kakaoAuth("abc")).willReturn(KakaoAuthResponse.signupRequired("signup-jwt", "민서"));

        mockMvc.perform(post("/v1/auth/kakao").param("code", "abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isNewUser").value(true))
                .andExpect(jsonPath("$.data.signupToken").value("signup-jwt"))
                .andExpect(jsonPath("$.data.kakaoNickname").value("민서"));
    }

    @Test
    @DisplayName("POST /v1/auth/kakao — code 누락 400, 카카오 실패 400 OAUTH_FAILED")
    void kakao_errors() throws Exception {
        mockMvc.perform(post("/v1/auth/kakao"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("code"));

        given(authService.kakaoAuth("bad")).willThrow(new BusinessException(ErrorCode.OAUTH_FAILED));
        mockMvc.perform(post("/v1/auth/kakao").param("code", "bad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("OAUTH_FAILED"));
    }

    @Test
    @DisplayName("POST /v1/auth/kakao/signup — 201, 동의 false는 400, 토큰 무효 401, 이미 가입 409")
    void kakaoSignup_statuses() throws Exception {
        given(authService.kakaoSignup(any())).willReturn(TOKEN);
        mockMvc.perform(post("/v1/auth/kakao/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signupToken\":\"signup-jwt\",\"privacyAgreed\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.userId").value(12));

        mockMvc.perform(post("/v1/auth/kakao/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signupToken\":\"signup-jwt\",\"privacyAgreed\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("privacyAgreed"));

        // 이미 스텁된 메서드를 given(...)으로 다시 호출하면 기존 스텁이 실행되므로 willThrow(...).given(...) 형태로 재스텁
        willThrow(new BusinessException(ErrorCode.INVALID_SIGNUP_TOKEN)).given(authService).kakaoSignup(any());
        mockMvc.perform(post("/v1/auth/kakao/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signupToken\":\"expired\",\"privacyAgreed\":true}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_SIGNUP_TOKEN"));

        willThrow(new BusinessException(ErrorCode.ALREADY_REGISTERED)).given(authService).kakaoSignup(any());
        mockMvc.perform(post("/v1/auth/kakao/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signupToken\":\"reused\",\"privacyAgreed\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_REGISTERED"));
    }
}

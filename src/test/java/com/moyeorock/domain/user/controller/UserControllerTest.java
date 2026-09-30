package com.moyeorock.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moyeorock.domain.user.dto.request.OnboardingCreateRequest;
import com.moyeorock.domain.user.dto.request.UserInstrumentRequest;
import com.moyeorock.domain.user.dto.request.UserInstrumentUpdateRequest;
import com.moyeorock.domain.user.dto.request.UserUpdateRequest;
import com.moyeorock.domain.user.dto.response.NicknameCheckResponse;
import com.moyeorock.domain.user.dto.response.UserInstrumentResponse;
import com.moyeorock.domain.user.dto.response.UserInstrumentsResponse;
import com.moyeorock.domain.user.dto.response.UserMeResponse;
import com.moyeorock.domain.user.dto.response.UserSummaryResponse;
import com.moyeorock.domain.user.dto.response.UserWithdrawResponse;
import com.moyeorock.domain.user.enums.LoginType;
import com.moyeorock.domain.user.enums.PlatformRole;
import com.moyeorock.domain.user.service.UserService;
import com.moyeorock.global.common.dto.PageResponse;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Level;
import com.moyeorock.global.common.enums.Region;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import com.moyeorock.global.security.UserAuthentication;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

// 필터를 끄고(addFilters=false) @AuthUser 리졸버가 읽는 SecurityContext만 직접 세팅한다.
// 응답의 null 필드 표기(jackson non_null, 팀장 확인 중 — 검토 문서 B-1)는 여기서 단언하지 않는다.
@WebMvcTest(controllers = UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    private static final Long USER_ID = 12L;

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @MockitoBean
    UserService userService;

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(new UserAuthentication(USER_ID));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private UserMeResponse meResponse() {
        return new UserMeResponse(USER_ID, "seojun@example.com", "서준", Region.SEOUL, List.of(Genre.ROCK),
                "베이스 3년차", null, PlatformRole.USER, LoginType.EMAIL,
                List.of(new UserInstrumentResponse(3L, Instrument.BASS, Level.INTERMEDIATE)),
                true, true, true, LocalDateTime.of(2026, 7, 2, 11, 20));
    }

    private String json(Object body) {
        return objectMapper.writeValueAsString(body);
    }

    @Test
    @DisplayName("GET /v1/users/me — 200, 응답 키가 camelCase이고 isRecommendable 키 이름이 그대로 나간다")
    void getMe_returns200() throws Exception {
        given(userService.getMe(USER_ID)).willReturn(meResponse());

        mockMvc.perform(get("/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(12))
                .andExpect(jsonPath("$.data.loginType").value("EMAIL"))
                .andExpect(jsonPath("$.data.isRecommendable").value(true))
                .andExpect(jsonPath("$.data.isActivityPublic").value(true))
                .andExpect(jsonPath("$.data.onboardingCompleted").value(true))
                .andExpect(jsonPath("$.data.instruments[0].instrument").value("BASS"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    @DisplayName("GET /v1/users/me — 인증 정보가 없으면 401 UNAUTHORIZED")
    void getMe_withoutAuth_returns401() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /v1/users/me — 탈퇴·정지 사용자는 404 USER_NOT_FOUND")
    void getMe_notFound_returns404() throws Exception {
        given(userService.getMe(USER_ID)).willThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

        mockMvc.perform(get("/v1/users/me"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("PUT /v1/users/me — 200")
    void updateMe_returns200() throws Exception {
        given(userService.updateMe(eq(USER_ID), any())).willReturn(meResponse());

        mockMvc.perform(put("/v1/users/me").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UserUpdateRequest("서준", Region.SEOUL, List.of(Genre.ROCK),
                                "베이스 3년차", null, true, true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("서준"));
    }

    @Test
    @DisplayName("PUT /v1/users/me — 닉네임 공백·불리언 누락은 400 VALIDATION_FAILED, fieldErrors에 필드명")
    void updateMe_invalid_returns400() throws Exception {
        mockMvc.perform(put("/v1/users/me").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\" \",\"isActivityPublic\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fieldErrors[*].field", containsInAnyOrder("nickname", "isRecommendable")));
    }

    @Test
    @DisplayName("PUT /v1/users/me — 닉네임 중복은 409 NICKNAME_DUPLICATED")
    void updateMe_duplicatedNickname_returns409() throws Exception {
        given(userService.updateMe(eq(USER_ID), any()))
                .willThrow(new BusinessException(ErrorCode.NICKNAME_DUPLICATED));

        mockMvc.perform(put("/v1/users/me").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UserUpdateRequest("민서", null, null, null, null, true, true))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("NICKNAME_DUPLICATED"));
    }

    @Test
    @DisplayName("DELETE /v1/users/me — 200, withdrawnAt")
    void withdraw_returns200() throws Exception {
        given(userService.withdraw(USER_ID))
                .willReturn(new UserWithdrawResponse(LocalDateTime.of(2026, 8, 11, 14, 2)));

        mockMvc.perform(delete("/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.withdrawnAt").value("2026-08-11T14:02:00"));
    }

    @Test
    @DisplayName("POST /v1/users/me/onboarding — 201이 아니라 200")
    void onboarding_returns200() throws Exception {
        given(userService.completeOnboarding(eq(USER_ID), any())).willReturn(meResponse());

        mockMvc.perform(post("/v1/users/me/onboarding").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new OnboardingCreateRequest("서준", Region.SEOUL, List.of(Genre.ROCK),
                                List.of(new UserInstrumentRequest(Instrument.BASS, Level.INTERMEDIATE))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboardingCompleted").value(true));
    }

    @Test
    @DisplayName("POST /v1/users/me/onboarding — 세션 비어 있음·항목 필드 누락은 400, 중첩 필드명이 fieldErrors에")
    void onboarding_invalid_returns400() throws Exception {
        mockMvc.perform(post("/v1/users/me/onboarding").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"서준\",\"region\":\"SEOUL\",\"genres\":[],\"instruments\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[*].field", hasItem("instruments")));

        mockMvc.perform(post("/v1/users/me/onboarding").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"서준\",\"region\":\"SEOUL\",\"genres\":[],"
                                + "\"instruments\":[{\"level\":\"NOVICE\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[*].field", hasItem("instruments[0].instrument")));
    }

    @Test
    @DisplayName("POST /v1/users/me/onboarding — 없는 enum 값은 400 VALIDATION_FAILED")
    void onboarding_unknownEnum_returns400() throws Exception {
        mockMvc.perform(post("/v1/users/me/onboarding").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"서준\",\"region\":\"SEOUL\",\"genres\":[],"
                                + "\"instruments\":[{\"instrument\":\"HARMONICA\",\"level\":\"NOVICE\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("PUT /v1/users/me/instruments — 200, 교체 후 목록")
    void updateInstruments_returns200() throws Exception {
        given(userService.updateInstruments(eq(USER_ID), any())).willReturn(new UserInstrumentsResponse(List.of(
                new UserInstrumentResponse(8L, Instrument.BASS, Level.ADVANCED),
                new UserInstrumentResponse(9L, Instrument.KEY, Level.NOVICE))));

        mockMvc.perform(put("/v1/users/me/instruments").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UserInstrumentUpdateRequest(List.of(
                                new UserInstrumentRequest(Instrument.BASS, Level.ADVANCED),
                                new UserInstrumentRequest(Instrument.KEY, Level.NOVICE))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.instruments.length()").value(2))
                .andExpect(jsonPath("$.data.instruments[1].id").value(9));
    }

    @Test
    @DisplayName("PUT /v1/users/me/instruments — 빈 목록은 400")
    void updateInstruments_empty_returns400() throws Exception {
        mockMvc.perform(put("/v1/users/me/instruments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instruments\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("instruments"));
    }

    @Test
    @DisplayName("GET /v1/users/nickname/check — 200, 요청값 그대로와 available")
    void checkNickname_returns200() throws Exception {
        given(userService.checkNickname("서준")).willReturn(new NicknameCheckResponse("서준", false));

        mockMvc.perform(get("/v1/users/nickname/check").param("nickname", "서준"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("서준"))
                .andExpect(jsonPath("$.data.available").value(false));
    }

    @Test
    @DisplayName("GET /v1/users/nickname/check — nickname 누락은 400, fieldErrors에 nickname")
    void checkNickname_missingParam_returns400() throws Exception {
        mockMvc.perform(get("/v1/users/nickname/check"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("nickname"));
    }

    @Test
    @DisplayName("GET /v1/users/search — 200, 기본 page=0·size=20이 Service에 전달된다")
    void search_defaultPageable() throws Exception {
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        given(userService.search(eq("서준"), pageable.capture())).willReturn(new PageResponse<>(
                List.of(new UserSummaryResponse(12L, "서준", null)), 0, 20, 1, 1, true));

        mockMvc.perform(get("/v1/users/search").param("nickname", "서준"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].nickname").value("서준"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.last").value(true));
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("GET /v1/users/search — size가 상한(20)을 넘으면 20으로 잘린다")
    void search_sizeCappedAt20() throws Exception {
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        given(userService.search(eq("서준"), pageable.capture()))
                .willReturn(new PageResponse<>(List.of(), 1, 20, 0, 0, true));

        mockMvc.perform(get("/v1/users/search").param("nickname", "서준").param("page", "1").param("size", "100"))
                .andExpect(status().isOk());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("GET /v1/users/search — nickname 누락은 400")
    void search_missingNickname_returns400() throws Exception {
        mockMvc.perform(get("/v1/users/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("nickname"));
    }
}

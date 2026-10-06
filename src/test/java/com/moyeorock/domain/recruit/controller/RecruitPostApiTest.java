package com.moyeorock.domain.recruit.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moyeorock.config.TestcontainersConfig;
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
    private static final long AUTHOR_ID = 7L;
    private static final long OTHER_ID = 8L;

    private static final String CREATE_BODY = """
            {"targetType":"TEAM","targetId":5,"title":"베이스 1명 구합니다","body":"주 1회 홍대에서 합주합니다.",
             "wantedSlots":[{"instrument":"BASS","count":1},{"instrument":"KEY","count":1}],"region":"SEOUL"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

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
        createPost(AUTHOR_ID, CREATE_BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.targetType").value("TEAM"))
                .andExpect(jsonPath("$.data.target.id").value(5))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.canEdit").value(true))
                .andExpect(jsonPath("$.data.wantedSlots[0].instrument").value("BASS"))
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.updatedAt").exists());
    }

    @Test
    @DisplayName("계산할 수 없는 appliedCount는 0이 아니라 키 자체가 응답에서 빠진다")
    void create_omitsAppliedCount_whenUnknown() throws Exception {
        createPost(AUTHOR_ID, CREATE_BODY)
                .andExpect(jsonPath("$.data.wantedSlots[0].appliedCount").doesNotExist());
    }

    @Test
    @DisplayName("모집 슬롯이 비어 있으면 400 VALIDATION_FAILED다")
    void create_withEmptyWantedSlots_returns400() throws Exception {
        createPost(AUTHOR_ID, """
                {"targetType":"TEAM","targetId":5,"title":"t","body":"b","wantedSlots":[],"region":"SEOUL"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("존재하지 않는 악기 값(폐지된 ETC 포함)은 400이다")
    void create_withUnknownInstrument_returns400() throws Exception {
        createPost(AUTHOR_ID, """
                {"targetType":"TEAM","targetId":5,"title":"t","body":"b",
                 "wantedSlots":[{"instrument":"ETC","count":1}],"region":"SEOUL"}
                """)
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("상세 조회: 작성자는 canEdit=true, 다른 사용자는 false다")
    void getDetail_canEditDependsOnViewer() throws Exception {
        long id = createPostAndGetId(AUTHOR_ID, CREATE_BODY);

        mockMvc.perform(get(BASE + "/" + id).header("Authorization", bearer(AUTHOR_ID)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.canEdit").value(true));
        mockMvc.perform(get(BASE + "/" + id).header("Authorization", bearer(OTHER_ID)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.canEdit").value(false));
    }

    @Test
    @DisplayName("없는 공고 조회는 404 RECRUIT_POST_NOT_FOUND다")
    void getDetail_notFound_returns404() throws Exception {
        mockMvc.perform(get(BASE + "/999999").header("Authorization", bearer(AUTHOR_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RECRUIT_POST_NOT_FOUND"));
    }

    @Test
    @DisplayName("목록: 쿼리 파라미터(targetType·instrument·status)로 걸러지고 페이지 응답 모양을 따른다")
    void search_filtersByQueryParams() throws Exception {
        createPostAndGetId(AUTHOR_ID, CREATE_BODY);
        createPostAndGetId(AUTHOR_ID, """
                {"targetType":"GROUP","targetId":9,"title":"모임 드러머","body":"b",
                 "wantedSlots":[{"instrument":"DRUM","count":1}],"region":"BUSAN"}
                """);

        mockMvc.perform(get(BASE).header("Authorization", bearer(OTHER_ID))
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
        mockMvc.perform(get(BASE).header("Authorization", bearer(OTHER_ID)).param("instrument", "NOPE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("목록: 임의 sort 파라미터가 와도 500이 아니라 최신순으로 정상 응답한다")
    void search_withArbitrarySortParam_stillOk() throws Exception {
        createPostAndGetId(AUTHOR_ID, CREATE_BODY);

        mockMvc.perform(get(BASE).header("Authorization", bearer(OTHER_ID)).param("sort", "noSuchProperty,asc"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("수정: 작성자는 200과 갱신된 상세를, 다른 사용자는 403 NOT_POST_AUTHOR를 받는다")
    void update_authorOk_otherForbidden() throws Exception {
        long id = createPostAndGetId(AUTHOR_ID, CREATE_BODY);
        String updateBody = """
                {"title":"수정된 제목","body":"수정된 본문","wantedSlots":[{"instrument":"DRUM","count":2}],"region":"BUSAN"}
                """;

        mockMvc.perform(put(BASE + "/" + id).header("Authorization", bearer(AUTHOR_ID))
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("수정된 제목"))
                .andExpect(jsonPath("$.data.region").value("BUSAN"))
                .andExpect(jsonPath("$.data.target.id").value(5));
        mockMvc.perform(put(BASE + "/" + id).header("Authorization", bearer(OTHER_ID))
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_POST_AUTHOR"));
    }

    @Test
    @DisplayName("마감: 작성자가 CLOSED로 바꾸면 200, 같은 요청을 또 하면 409 INVALID_STATE다")
    void close_thenCloseAgain_returns409() throws Exception {
        long id = createPostAndGetId(AUTHOR_ID, CREATE_BODY);
        String closeBody = "{\"status\":\"CLOSED\"}";

        mockMvc.perform(patch(BASE + "/" + id + "/status").header("Authorization", bearer(AUTHOR_ID))
                        .contentType(MediaType.APPLICATION_JSON).content(closeBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.status").value("CLOSED"));
        mockMvc.perform(patch(BASE + "/" + id + "/status").header("Authorization", bearer(AUTHOR_ID))
                        .contentType(MediaType.APPLICATION_JSON).content(closeBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATE"));
    }
}

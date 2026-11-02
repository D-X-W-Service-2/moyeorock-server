package com.moyeorock.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.jayway.jsonpath.JsonPath;
import com.moyeorock.config.TestcontainersConfig;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** 실제로 생성된 /v3/api-docs로 Swagger 전역 보정(OpenApiConfig)이 먹었는지 확인한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class OpenApiDocsTest {

    @Autowired
    private MockMvc mockMvc;

    private String docs;

    @BeforeEach
    void loadDocs() throws Exception {
        docs = mockMvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();
    }

    private <T> T read(String path) {
        return JsonPath.read(docs, path);
    }

    @Test
    @DisplayName("Pageable은 필수 객체 파라미터가 아니라 page·size로 펼쳐지고 sort는 없다")
    void pageable_isFlattenedToPageAndSize() {
        List<String> names = read("$.paths['/v1/teams'].get.parameters[*].name");
        assertThat(names).contains("page", "size").doesNotContain("pageable", "sort");

        List<Integer> maxSize = read("$.paths['/v1/teams'].get.parameters[?(@.name=='size')].schema.maximum");
        assertThat(maxSize).containsExactly(20);
        List<Boolean> required = read("$.paths['/v1/teams'].get.parameters[?(@.name=='page')].required");
        assertThat(required).containsExactly(false);
    }

    @Test
    @DisplayName("검색 조건 객체는 필드별 쿼리 파라미터로 펼쳐진다")
    void searchCondition_isFlattenedToFields() {
        List<String> names = read("$.paths['/v1/recruit-post'].get.parameters[*].name");
        assertThat(names).contains("targetType", "region", "instrument", "status", "page", "size")
                .doesNotContain("condition", "pageable");
    }

    @Test
    @DisplayName("펼치고 남은 Pageable·검색 조건 스키마는 문서에서 빠진다")
    void flattenedSchemas_areRemoved() {
        java.util.Map<String, Object> schemas = read("$.components.schemas");
        assertThat(schemas).doesNotContainKeys("Pageable", "RecruitPostSearchCondition");
    }

    @Test
    @DisplayName("태그는 클래스명이 아니라 도메인 한글 이름이다")
    void tags_areDomainNames() {
        List<String> teamTags = read("$.paths['/v1/teams'].get.tags");
        assertThat(teamTags).containsExactly("팀");
        List<String> recruitTags = read("$.paths['/v1/recruit-post'].get.tags");
        assertThat(recruitTags).containsExactly("공고");

        List<List<String>> all = read("$.paths.*.*.tags");
        assertThat(all.stream().flatMap(List::stream)).noneMatch(tag -> tag.endsWith("-controller"));
    }

    @Test
    @DisplayName("응답 미디어 타입은 */*가 아니라 application/json이다")
    void responses_areJson() {
        assertThat(docs).doesNotContain("\"*/*\"");
        Object content = read("$.paths['/v1/teams'].get.responses['200'].content['application/json']");
        assertThat(content).isNotNull();
    }

    @Test
    @DisplayName("인증이 필요한 엔드포인트에는 401이, 입력이 있는 엔드포인트에는 400이 공통으로 달린다")
    void commonErrorResponses_areAttached() {
        Object unauthorized = read("$.paths['/v1/teams'].get.responses['401'].content['application/json'].schema");
        assertThat(unauthorized.toString()).contains("ErrorResponse");
        Object badRequest = read("$.paths['/v1/teams'].get.responses['400']");
        assertThat(badRequest).isNotNull();

        String code = read("$.components.schemas.ErrorResponse.properties.code.example");
        assertThat(code).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("@AuthUser 파라미터는 문서에 나오지 않는다")
    void authUser_isHidden() {
        List<String> names = read("$.paths['/v1/teams'].get.parameters[*].name");
        assertThat(names).doesNotContain("userId");
    }
}

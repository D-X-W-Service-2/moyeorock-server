package com.moyeorock.domain.song.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.jayway.jsonpath.JsonPath;
import com.moyeorock.domain.song.service.SongRecommendationService;
import com.moyeorock.domain.song.service.RecommendationEventService;
import com.moyeorock.domain.song.service.SongService;
import com.moyeorock.global.config.OpenApiConfig;
import java.util.List;
import java.util.Map;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.configuration.SpringDocPageableConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {SongRecommendationController.class, RecommendationFeedbackController.class,
        SongController.class},
        properties = {"springdoc.api-docs.enabled=true"})
@AutoConfigureMockMvc(addFilters = false)
@Import(OpenApiConfig.class)
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocWebMvcConfiguration.class,
        SpringDocConfigProperties.class, SpringDocPageableConfiguration.class})
class SongRecommendationOpenApiTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private SongRecommendationService recommendationService;
    @MockitoBean private RecommendationEventService recommendationEventService;
    @MockitoBean private SongService songService;

    @Test
    void generatedSwaggerHasBothRecommendationContracts() throws Exception {
        String docs = mockMvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(docs,
                "$.paths['/v1/users/me/songs/recommendations'].get.summary"))
                .isEqualTo("내 취향 추천곡");
        assertThat(JsonPath.<String>read(docs,
                "$.paths['/v1/teams/{teamId}/songs/recommendations'].get.summary"))
                .isEqualTo("팀 곡 추천");
        assertThat(JsonPath.<String>read(docs,
                "$.paths['/v1/recommendations/{requestId}/feedback'].post.summary"))
                .isEqualTo("추천곡 후속 행동 기록");
        Map<String, Object> personal = JsonPath.read(docs,
                "$.paths['/v1/users/me/songs/recommendations'].get");
        assertThat(personal).doesNotContainKeys("parameters", "requestBody");
        List<String> teamParams = JsonPath.read(docs,
                "$.paths['/v1/teams/{teamId}/songs/recommendations'].get.parameters[*].name");
        assertThat(teamParams).containsExactly("teamId");
        assertThat(JsonPath.<String>read(docs,
                "$.paths['/v1/teams/{teamId}/songs/recommendations'].get.responses['200'].content['application/json'].schema['$ref']"))
                .contains("ApiResponseTeamSongRecommendationResponse");
        assertThat(docs).contains("SongRecommendationResponse", "TeamSongRecommendationResponse",
                "selectedByOtherTeam", "reasons");
        assertThat(JsonPath.<String>read(docs,
                "$.paths['/v1/teams/{teamId}/songs/recommendations'].get.responses['403'].description"))
                .contains("NO_PERMISSION");
        assertThat(JsonPath.<String>read(docs,
                "$.paths['/v1/teams/{teamId}/songs/recommendations'].get.responses['404'].description"))
                .contains("TEAM_NOT_FOUND");

        List<String> searchParams = JsonPath.read(docs, "$.paths['/v1/songs'].get.parameters[*].name");
        assertThat(searchParams).contains("page", "size").doesNotContain("pageable", "userId");

    }
}

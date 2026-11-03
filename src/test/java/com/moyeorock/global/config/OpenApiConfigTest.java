package com.moyeorock.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 공개 엔드포인트 처리처럼 현재 main에 해당 컨트롤러가 없는 경우를 위해 커스터마이저를 직접 돌려 본다. */
class OpenApiConfigTest {

    private static Operation operation() {
        Operation operation = new Operation();
        operation.setRequestBody(new RequestBody().content(
                new Content().addMediaType("application/json", new MediaType().schema(new Schema<>()))));
        operation.setResponses(new ApiResponses().addApiResponse("200", new ApiResponse().description("OK")));
        return operation;
    }

    private static OpenAPI docsWith(String path) {
        OpenAPI openApi = new OpenAPI().components(new Components());
        openApi.setPaths(new Paths().addPathItem(path, new PathItem().post(operation())));
        new OpenApiConfig().globalDocsCustomizer(20, 20).customise(openApi);
        return openApi;
    }

    @Test
    @DisplayName("공개 엔드포인트(/v1/auth/**)는 인증 표시가 없고 401도 달리지 않는다")
    void publicEndpoint_hasNoSecurityAndNo401() {
        Operation login = docsWith("/v1/auth/login").getPaths().get("/v1/auth/login").getPost();

        assertThat(login.getSecurity()).isEmpty();
        assertThat(login.getResponses()).doesNotContainKey("401");
        assertThat(login.getResponses()).containsKey("400");
    }

    @Test
    @DisplayName("공개가 아닌 엔드포인트는 전역 인증을 그대로 쓰고 401이 달린다")
    void protectedEndpoint_keepsSecurityAndGets401() {
        Operation create = docsWith("/v1/teams").getPaths().get("/v1/teams").getPost();

        assertThat(create.getSecurity()).isNull();
        assertThat(create.getResponses()).containsKeys("400", "401");
    }

    @Test
    @DisplayName("Swagger 자체 경로도 공개라 인증 표시가 없다")
    void swaggerPaths_arePublic() {
        Operation docs = docsWith("/v3/api-docs").getPaths().get("/v3/api-docs").getPost();

        assertThat(docs.getSecurity()).isEmpty();
    }
}

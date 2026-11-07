package com.moyeorock.global.config;

import com.moyeorock.global.security.AuthUser;
import com.moyeorock.global.security.InternalApiKeyFilter;
import com.moyeorock.global.security.PublicEndpoints;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.AntPathMatcher;

/**
 * Swagger 전역 설정. 컨트롤러마다 어노테이션을 붙이지 않고 여기서 한 번에 처리한다 — 새 컨트롤러도 자동으로 적용된다.
 * 컨트롤러가 붙일 것은 {@code @Operation(summary = ...)}뿐이다(api-conventions.md §9).
 */
@Configuration
public class OpenApiConfig {

    // @AuthUser 파라미터는 토큰에서 주입되는 값이라 Swagger 요청 파라미터로 노출하지 않는다.
    static {
        SpringDocUtils.getConfig().addAnnotationsToIgnore(AuthUser.class);
    }

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";
    // 내부 서버(AI 서버) 전용 /v1/internal/**는 JWT 대신 API 키 헤더로 인증한다(InternalApiKeyFilter, 이슈 #147)
    private static final String INTERNAL_SECURITY_SCHEME_NAME = "internalApiKey";
    private static final String ERROR_SCHEMA_NAME = "ErrorResponse";
    private static final String JSON = "application/json";

    // 컨트롤러 패키지(domain/{이름}/controller, global/{이름}/controller)의 {이름} → 태그 표시 이름.
    // 목록에 없는 이름은 패키지 이름 그대로 쓴다.
    private static final Map<String, String> DOMAIN_TAGS = Map.ofEntries(
            Map.entry("auth", "로그인"),
            Map.entry("user", "사용자"),
            Map.entry("team", "팀"),
            Map.entry("rehearsal", "합주"),
            Map.entry("group", "모임"),
            Map.entry("notice", "공지"),
            Map.entry("performance", "공연"),
            Map.entry("song", "노래"),
            Map.entry("setlist", "팀 선택곡"),
            Map.entry("recruit", "공고"),
            Map.entry("join", "가입 신청"),
            Map.entry("notification", "알림"),
            Map.entry("bookmark", "저장"),
            Map.entry("file", "파일"));

    @Bean
    public OpenAPI moyeorockOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("모여락 API")
                        .description("밴드 통합 관리 플랫폼")
                        .version("v1.0.0")
                        .contact(new Contact().name("moyeorock")))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT"))
                        .addSecuritySchemes(INTERNAL_SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name(InternalApiKeyFilter.HEADER)))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }

    /** 태그를 클래스명(team-controller) 대신 도메인 이름으로 붙인다. */
    @Bean
    public OperationCustomizer domainTagCustomizer() {
        return (operation, handlerMethod) -> {
            operation.setTags(List.of(tagOf(handlerMethod.getBeanType())));
            return operation;
        };
    }

    /**
     * 문서 전체에 한 번에 적용하는 보정(컨트롤러에 어노테이션을 붙이지 않는다).
     * <ul>
     *   <li>{@code Pageable}과 검색 조건 객체를 필수 객체 파라미터 하나가 아니라 쿼리 파라미터로 펼친다</li>
     *   <li>응답 미디어 타입 {@code *}{@code /*}를 {@code application/json}으로 바꾼다</li>
     *   <li>공개 엔드포인트({@link PublicEndpoints})는 인증 표시를 없애고, 내부 API({@code /v1/internal/**})는 JWT 대신
     *       API 키 헤더로 표시한다. 공개가 아니면 401을, 요청 값이 있으면 400을 단다.
     *       404·409처럼 엔드포인트마다 다른 코드는 자동으로 알 수 없어 붙이지 않는다(규약: @ApiResponses를 장황하게 달지 않는다)</li>
     * </ul>
     */
    @Bean
    public OpenApiCustomizer globalDocsCustomizer(
            @Value("${spring.data.web.pageable.default-page-size:20}") int defaultPageSize,
            @Value("${spring.data.web.pageable.max-page-size:20}") int maxPageSize) {
        return openApi -> {
            openApi.getComponents().addSchemas(ERROR_SCHEMA_NAME, errorSchema());
            AntPathMatcher matcher = new AntPathMatcher();
            Set<String> flattened = new HashSet<>();
            openApi.getPaths().forEach((path, item) -> item.readOperations().forEach(operation -> {
                flattenObjectParameters(openApi, operation, defaultPageSize, maxPageSize, flattened);
                useJsonMediaType(operation);
                if (isPublic(matcher, path)) {
                    operation.setSecurity(List.of());
                } else if (matcher.match(InternalApiKeyFilter.PATH_PATTERN, path)) {
                    operation.setSecurity(List.of(new SecurityRequirement().addList(INTERNAL_SECURITY_SCHEME_NAME)));
                    addError(operation, "401", "내부 API 키가 필요합니다.");
                } else {
                    addError(operation, "401", "인증이 필요합니다.");
                }
                if (operation.getRequestBody() != null
                        || (operation.getParameters() != null && !operation.getParameters().isEmpty())) {
                    addError(operation, "400", "입력값이 올바르지 않습니다.");
                }
            }));
            removeUnusedSchemas(openApi, flattened);
        };
    }

    // 쿼리 파라미터가 객체 하나(pageable, condition)로 잡힌 것을 필드별 파라미터로 푼다. Pageable의 sort는 뺀다 —
    // 정렬은 서버가 고정하고 클라이언트 sort는 쓰지 않기 때문이다(conventions.md).
    private static void flattenObjectParameters(OpenAPI openApi, Operation operation, int defaultPageSize,
            int maxPageSize, Set<String> flattened) {
        if (operation.getParameters() == null) {
            return;
        }
        List<Parameter> result = new ArrayList<>();
        for (Parameter parameter : operation.getParameters()) {
            String ref = parameter.getSchema() == null ? null : parameter.getSchema().get$ref();
            if (!"query".equals(parameter.getIn()) || ref == null) {
                result.add(parameter);
                continue;
            }
            String name = ref.substring(ref.lastIndexOf('/') + 1);
            Schema<?> schema = openApi.getComponents().getSchemas().get(name);
            if ("Pageable".equals(name)) {
                result.add(queryParameter("page", "페이지 번호(0부터)",
                        new IntegerSchema().minimum(BigDecimal.ZERO)._default(0)));
                result.add(queryParameter("size", "페이지 크기",
                        new IntegerSchema().minimum(BigDecimal.ONE).maximum(BigDecimal.valueOf(maxPageSize))
                                ._default(defaultPageSize)));
                flattened.add(name);
            } else if (schema != null && "object".equals(schema.getType()) && schema.getProperties() != null) {
                schema.getProperties().forEach((field, fieldSchema) ->
                        result.add(queryParameter(field, null, fieldSchema)));
                flattened.add(name);
            } else {
                result.add(parameter);
            }
        }
        operation.setParameters(result);
    }

    private static Parameter queryParameter(String name, String description, Schema<?> schema) {
        return new Parameter().name(name).in("query").required(false).description(description).schema(schema);
    }

    private static void useJsonMediaType(Operation operation) {
        operation.getResponses().values().forEach(response -> {
            Content content = response.getContent();
            if (content != null && content.containsKey("*/*")) {
                MediaType any = content.remove("*/*");
                content.addMediaType(JSON, any);
            }
        });
    }

    // 펼친 뒤 아무 데서도 참조하지 않는 객체 스키마(Pageable, 검색 조건)는 문서에서 뺀다
    private static void removeUnusedSchemas(OpenAPI openApi, Set<String> candidates) {
        String serialized = Json.pretty(openApi);
        candidates.stream()
                .filter(name -> !serialized.contains("#/components/schemas/" + name + "\""))
                .forEach(name -> openApi.getComponents().getSchemas().remove(name));
    }

    private static String tagOf(Class<?> controller) {
        String[] segments = controller.getPackageName().split("\\.");
        for (int i = 0; i < segments.length - 1; i++) {
            if (segments[i].equals("domain") || segments[i].equals("global")) {
                String name = segments[i + 1];
                // 가입 신청과 초대는 한 도메인(join)이지만 컨트롤러가 둘이고 화면에서도 따로 쓴다
                if (controller.getSimpleName().equals("InvitationController")) {
                    return "초대";
                }
                return DOMAIN_TAGS.getOrDefault(name, name);
            }
        }
        return controller.getSimpleName();
    }

    private static boolean isPublic(AntPathMatcher matcher, String path) {
        return Arrays.stream(PublicEndpoints.paths()).anyMatch(pattern -> matcher.match(pattern, path));
    }

    private static void addError(Operation operation, String status, String description) {
        operation.getResponses().addApiResponse(status, new ApiResponse()
                .description(description)
                .content(new Content().addMediaType(JSON,
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + ERROR_SCHEMA_NAME)))));
    }

    // 실패 응답 봉투(api-conventions.md §1) — data는 항상 null이다
    private static Schema<?> errorSchema() {
        return new ObjectSchema()
                .addProperty("success", new Schema<Boolean>().type("boolean").example(false))
                .addProperty("code", new StringSchema().description("ErrorCode 상수명").example("VALIDATION_FAILED"))
                .addProperty("message", new StringSchema().example("입력값이 올바르지 않습니다."))
                .addProperty("data", new ObjectSchema().nullable(true));
    }
}

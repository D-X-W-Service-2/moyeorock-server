package com.moyeorock.global.config;

import com.moyeorock.global.security.AuthUser;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    // @AuthUser는 토큰에서 주입되는 값이라 클라이언트가 보내는 파라미터가 아니다 — 문서에 노출하지 않는다
    static {
        SpringDocUtils.getConfig().addAnnotationsToIgnore(AuthUser.class);
    }

    @Bean
    public OpenAPI finSightOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("모여락 API")
                        .description(
                                "밴드 통합 관리 플랫폼"
                        )
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("moyeorock")))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement()
                        .addList(SECURITY_SCHEME_NAME));
    }
}
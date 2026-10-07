package com.moyeorock.global.security;

import java.util.Arrays;

public enum PublicEndpoints {

    // 로그인·회원가입은 인증 없이 열어야 한다. 경로가 실제 엔드포인트(/v1/auth/login·signup·kakao)와
    // 어긋나면 로그인 자체가 인증을 요구하는 순환에 빠진다 — api-conventions.md §5 접두사와 함께 본다.
    AUTH("/v1/auth/**"),
    SWAGGER_UI("/swagger-ui.html"),
    SWAGGER_UI_RESOURCES("/swagger-ui/**"),
    API_DOCS("/v3/api-docs"),
    API_DOCS_RESOURCES("/v3/api-docs/**");

    private final String path;

    PublicEndpoints(String path) {
        this.path = path;
    }

    public static String[] paths() {
        return Arrays.stream(values())
                .map(endpoint -> endpoint.path)
                .toArray(String[]::new);
    }
}

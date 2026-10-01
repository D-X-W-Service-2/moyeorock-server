package com.moyeorock.global.security;

import java.util.Arrays;

public enum PublicEndpoints {

    AUTH("/v0/auth/**"),
    SWAGGER_UI("/swagger-ui.html"),
    SWAGGER_UI_RESOURCES("/swagger-ui/**"),
    API_DOCS("/v3/api-docs"),
    API_DOCS_RESOURCES("/v3/api-docs/**"),
    // 프로필·커버·포스터 이미지는 공개 정보라 인증 없이 연다(로컬 스토리지 임시 서빙 엔드포인트)
    LOCAL_FILE_SERVING("/v1/files/local/**");

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

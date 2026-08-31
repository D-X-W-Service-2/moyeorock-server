package com.moyeorock.global.security;

import java.util.Arrays;

public enum PublicEndpoints {

    AUTH("/api/auth/**"),
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

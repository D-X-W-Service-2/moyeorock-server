package com.moyeorock.domain.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 카카오 로그인 설정. client-id·redirect-uri는 환경변수(.env), URI 2개는 yml 기본값.
 * redirect-uri는 프론트가 인가 코드를 받은 URI와 정확히 일치해야 토큰 교환이 성공한다.
 */
@ConfigurationProperties(prefix = "kakao")
public record KakaoProperties(
        String clientId,
        String clientSecret,
        String redirectUri,
        String tokenUri,
        String userInfoUri
) {
}

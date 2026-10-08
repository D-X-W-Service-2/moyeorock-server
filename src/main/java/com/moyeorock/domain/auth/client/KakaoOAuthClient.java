package com.moyeorock.domain.auth.client;

import com.moyeorock.domain.auth.config.KakaoProperties;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 카카오 OAuth 호출 2개: 인가 코드 → 액세스 토큰 교환, 액세스 토큰 → 사용자 조회.
 * Service 안에 두면 테스트에서 카카오를 끊을 수 없어 별도 클래스로 둔다(인터페이스 없음, Mockito로 대체).
 * 카카오 쪽 실패(4xx·5xx·타임아웃)는 전부 400 OAUTH_FAILED 하나로 낸다 — 프론트 대응이 "다시 시도"로 같다.
 */
@Slf4j
@Component
public class KakaoOAuthClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);
    private static final String DEFAULT_NICKNAME = "카카오회원";
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
    };

    private final KakaoProperties properties;
    private final RestClient restClient;

    @Autowired
    public KakaoOAuthClient(KakaoProperties properties) {
        this(properties, RestClient.builder().requestFactory(defaultRequestFactory()).build());
    }

    // 테스트용: MockRestServiceServer를 바인딩한 RestClient 주입
    KakaoOAuthClient(KakaoProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    /** 인가 코드로 카카오 사용자(id·닉네임)를 얻는다. */
    public KakaoUserInfo fetchUser(String code) {
        String accessToken = exchangeCode(code);
        return fetchUserInfo(accessToken);
    }

    private String exchangeCode(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", properties.clientId());
        form.add("redirect_uri", properties.redirectUri());
        form.add("code", code);
        if (StringUtils.hasText(properties.clientSecret())) {
            form.add("client_secret", properties.clientSecret());
        }
        Map<String, Object> body = call(() -> restClient.post()
                .uri(properties.tokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(MAP), "토큰 교환");
        Object token = body == null ? null : body.get("access_token");
        if (token == null) {
            log.warn("카카오 토큰 교환 응답에 access_token 없음");
            throw new BusinessException(ErrorCode.OAUTH_FAILED);
        }
        return token.toString();
    }

    private KakaoUserInfo fetchUserInfo(String accessToken) {
        Map<String, Object> body = call(() -> restClient.get()
                .uri(properties.userInfoUri())
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(MAP), "사용자 조회");
        Object id = body == null ? null : body.get("id");
        if (id == null) {
            log.warn("카카오 사용자 조회 응답에 id 없음");
            throw new BusinessException(ErrorCode.OAUTH_FAILED);
        }
        return new KakaoUserInfo(String.valueOf(id), extractNickname(body));
    }

    /** properties.nickname → kakao_account.profile.nickname → 기본값. 동의 항목에 따라 비어 있을 수 있다. */
    @SuppressWarnings("unchecked")
    private String extractNickname(Map<String, Object> body) {
        Object properties = body.get("properties");
        if (properties instanceof Map<?, ?> props && props.get("nickname") instanceof String nickname
                && StringUtils.hasText(nickname)) {
            return nickname;
        }
        Object account = body.get("kakao_account");
        if (account instanceof Map<?, ?> acc && acc.get("profile") instanceof Map<?, ?> profile
                && profile.get("nickname") instanceof String nickname && StringUtils.hasText(nickname)) {
            return nickname;
        }
        return DEFAULT_NICKNAME;
    }

    private <T> T call(java.util.function.Supplier<T> request, String step) {
        try {
            return request.get();
        } catch (RestClientException e) {
            log.warn("카카오 {} 실패: {}", step, e.getMessage());
            throw new BusinessException(ErrorCode.OAUTH_FAILED);
        }
    }

    private static JdkClientHttpRequestFactory defaultRequestFactory() {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }
}

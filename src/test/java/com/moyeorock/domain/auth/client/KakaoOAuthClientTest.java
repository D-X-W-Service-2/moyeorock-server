package com.moyeorock.domain.auth.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.moyeorock.domain.auth.config.KakaoProperties;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

// 실제 카카오를 호출하지 않는다. MockRestServiceServer로 두 요청(토큰 교환·사용자 조회)을 스텁한다.
class KakaoOAuthClientTest {

    private static final String TOKEN_URI = "https://kauth.example/oauth/token";
    private static final String USER_INFO_URI = "https://kapi.example/v2/user/me";

    MockRestServiceServer server;
    KakaoOAuthClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        KakaoProperties properties = new KakaoProperties("client-id", "", "http://front/callback", TOKEN_URI, USER_INFO_URI);
        client = new KakaoOAuthClient(properties, builder.build());
    }

    private void stubTokenExchange() {
        server.expect(requestTo(TOKEN_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(containsString("grant_type=authorization_code")))
                .andExpect(content().string(containsString("client_id=client-id")))
                .andExpect(content().string(containsString("code=abc123")))
                .andRespond(withSuccess("{\"access_token\":\"kakao-at\",\"token_type\":\"bearer\"}", MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("인가 코드로 토큰을 교환하고 Bearer로 사용자 조회해 id·닉네임을 돌려준다")
    void fetchUser_success() {
        stubTokenExchange();
        server.expect(requestTo(USER_INFO_URI))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer kakao-at"))
                .andRespond(withSuccess("{\"id\":123456789,\"properties\":{\"nickname\":\"서준\"}}", MediaType.APPLICATION_JSON));

        KakaoUserInfo info = client.fetchUser("abc123");

        assertThat(info.kakaoId()).isEqualTo("123456789");
        assertThat(info.nickname()).isEqualTo("서준");
        server.verify();
    }

    @Test
    @DisplayName("properties.nickname이 없으면 kakao_account.profile.nickname, 그것도 없으면 기본값")
    void nickname_fallback() {
        stubTokenExchange();
        server.expect(requestTo(USER_INFO_URI))
                .andRespond(withSuccess("{\"id\":1,\"kakao_account\":{\"profile\":{\"nickname\":\"민서\"}}}", MediaType.APPLICATION_JSON));
        assertThat(client.fetchUser("abc123").nickname()).isEqualTo("민서");

        server.reset();
        stubTokenExchange();
        server.expect(requestTo(USER_INFO_URI))
                .andRespond(withSuccess("{\"id\":2}", MediaType.APPLICATION_JSON));
        assertThat(client.fetchUser("abc123").nickname()).isEqualTo("카카오회원");
    }

    @Test
    @DisplayName("토큰 교환이 4xx면 OAUTH_FAILED (만료·잘못된 인가 코드)")
    void tokenExchange_4xx_throwsOauthFailed() {
        server.expect(requestTo(TOKEN_URI)).andRespond(withBadRequest());

        Throwable thrown = catchThrowable(() -> client.fetchUser("expired"));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(ErrorCode.OAUTH_FAILED);
    }

    @Test
    @DisplayName("사용자 조회가 5xx면 OAUTH_FAILED")
    void userInfo_5xx_throwsOauthFailed() {
        stubTokenExchange();
        server.expect(requestTo(USER_INFO_URI)).andRespond(withServerError());

        Throwable thrown = catchThrowable(() -> client.fetchUser("abc123"));

        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(ErrorCode.OAUTH_FAILED);
    }

    @Test
    @DisplayName("응답에 access_token이나 id가 없어도 OAUTH_FAILED")
    void missingFields_throwsOauthFailed() {
        server.expect(requestTo(TOKEN_URI)).andRespond(withSuccess("{\"token_type\":\"bearer\"}", MediaType.APPLICATION_JSON));

        Throwable thrown = catchThrowable(() -> client.fetchUser("abc123"));

        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(ErrorCode.OAUTH_FAILED);
    }
}

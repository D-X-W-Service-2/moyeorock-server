package com.moyeorock.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class InternalApiKeyFilterTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("헤더 키가 설정값과 같으면 인증된 컨텍스트를 설정하고 다음 필터로 넘긴다")
    void setsAuthentication_whenKeyMatches() throws Exception {
        AtomicBoolean passed = new AtomicBoolean();

        new InternalApiKeyFilter("secret-key")
                .doFilterInternal(requestWithKey("secret-key"), new MockHttpServletResponse(), (req, res) -> passed.set(true));

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(passed).isTrue();
    }

    @Test
    @DisplayName("헤더가 없거나 키가 다르면 인증하지 않고 넘긴다 — 막는 것은 인가 단계(401)다")
    void doesNotAuthenticate_whenKeyMissingOrWrong() throws Exception {
        InternalApiKeyFilter filter = new InternalApiKeyFilter("secret-key");

        filter.doFilterInternal(new MockHttpServletRequest(), new MockHttpServletResponse(), (req, res) -> {});
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        filter.doFilterInternal(requestWithKey("secret-key-2"), new MockHttpServletResponse(), (req, res) -> {});
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("설정 키가 비어 있으면 빈 헤더를 포함해 어떤 요청도 인증하지 않는다 — 설정을 빠뜨린 배포에서 내부 API가 열리지 않는다")
    void rejectsEverything_whenConfiguredKeyBlank() throws Exception {
        for (String configured : new String[] {null, "", "   "}) {
            InternalApiKeyFilter filter = new InternalApiKeyFilter(configured);

            filter.doFilterInternal(requestWithKey(""), new MockHttpServletResponse(), (req, res) -> {});
            filter.doFilterInternal(requestWithKey("   "), new MockHttpServletResponse(), (req, res) -> {});

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }

    private static MockHttpServletRequest requestWithKey(String key) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(InternalApiKeyFilter.HEADER, key);
        return request;
    }
}

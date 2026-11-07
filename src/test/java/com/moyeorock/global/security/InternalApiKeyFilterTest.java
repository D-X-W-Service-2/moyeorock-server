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

    // MIN_KEY_BYTES(32) 이상이어야 쓰인다
    private static final String KEY = "secret-key-0123456789abcdefghijklmn";

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("헤더 키가 설정값과 같으면 인증된 컨텍스트를 설정하고 다음 필터로 넘긴다")
    void setsAuthentication_whenKeyMatches() throws Exception {
        AtomicBoolean passed = new AtomicBoolean();

        new InternalApiKeyFilter(KEY)
                .doFilterInternal(requestWithKey(KEY), new MockHttpServletResponse(), (req, res) -> passed.set(true));

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(passed).isTrue();
    }

    @Test
    @DisplayName("헤더가 없거나 키가 다르면 인증하지 않고 넘긴다 — 막는 것은 인가 단계(401)다")
    void doesNotAuthenticate_whenKeyMissingOrWrong() throws Exception {
        InternalApiKeyFilter filter = new InternalApiKeyFilter(KEY);

        filter.doFilterInternal(new MockHttpServletRequest(), new MockHttpServletResponse(), (req, res) -> {});
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        filter.doFilterInternal(requestWithKey(KEY + "-2"), new MockHttpServletResponse(), (req, res) -> {});
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

    @Test
    @DisplayName("설정 키가 32바이트보다 짧으면 같은 키를 보내도 인증하지 않는다 — 추측하기 쉬운 키로 내부 API가 열리지 않는다")
    void rejectsEverything_whenConfiguredKeyTooShort() throws Exception {
        String shortKey = "a".repeat(InternalApiKeyFilter.MIN_KEY_BYTES - 1);
        InternalApiKeyFilter filter = new InternalApiKeyFilter(shortKey);

        filter.doFilterInternal(requestWithKey(shortKey), new MockHttpServletResponse(), (req, res) -> {});

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("설정 키가 정확히 32바이트면 쓰인다")
    void acceptsKey_whenExactlyMinLength() throws Exception {
        String minKey = "a".repeat(InternalApiKeyFilter.MIN_KEY_BYTES);

        new InternalApiKeyFilter(minKey)
                .doFilterInternal(requestWithKey(minKey), new MockHttpServletResponse(), (req, res) -> {});

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    private static MockHttpServletRequest requestWithKey(String key) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(InternalApiKeyFilter.HEADER, key);
        return request;
    }
}

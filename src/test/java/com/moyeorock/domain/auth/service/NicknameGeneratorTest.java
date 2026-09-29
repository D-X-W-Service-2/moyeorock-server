package com.moyeorock.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.moyeorock.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NicknameGeneratorTest {

    @Mock
    UserRepository userRepository;
    @InjectMocks
    NicknameGenerator generator;

    @Test
    @DisplayName("겹치지 않으면 이름을 그대로 쓴다")
    void uses_name_as_is_when_free() {
        given(userRepository.existsByNickname("서준")).willReturn(false);

        assertThat(generator.resolve("서준")).isEqualTo("서준");
    }

    @Test
    @DisplayName("겹치면 '_'+영숫자 4자를 붙여 20자 이내로 만든다")
    void appends_suffix_when_taken() {
        given(userRepository.existsByNickname(anyString())).willAnswer(inv -> inv.getArgument(0).equals("서준"));

        String resolved = generator.resolve("서준");

        assertThat(resolved).matches("서준_[a-z0-9]{4}");
        assertThat(resolved.length()).isLessThanOrEqualTo(20);
    }

    @Test
    @DisplayName("15자를 넘는 이름은 15자로 자른 뒤 처리한다")
    void truncates_long_name() {
        String longName = "가".repeat(30);
        given(userRepository.existsByNickname("가".repeat(15))).willReturn(false);

        assertThat(generator.resolve(longName)).isEqualTo("가".repeat(15));
    }

    @Test
    @DisplayName("'탈퇴회원' 접두사 이름은 중복 여부와 무관하게 처음부터 suffix를 붙인다")
    void reserved_prefix_always_gets_suffix() {
        given(userRepository.existsByNickname(anyString())).willReturn(false);

        String resolved = generator.resolve("탈퇴회원");

        assertThat(resolved).matches("탈퇴회원_[a-z0-9]{4}");
        verify(userRepository, never()).existsByNickname("탈퇴회원");
    }

    @Test
    @DisplayName("빈 이름은 '회원'을 기본 이름으로 쓴다")
    void blank_name_falls_back() {
        given(userRepository.existsByNickname("회원")).willReturn(false);

        assertThat(generator.resolve("   ")).isEqualTo("회원");
    }
}

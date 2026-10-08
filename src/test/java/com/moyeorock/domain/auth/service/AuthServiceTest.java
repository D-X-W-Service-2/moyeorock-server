package com.moyeorock.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.moyeorock.domain.auth.client.KakaoOAuthClient;
import com.moyeorock.domain.auth.client.KakaoUserInfo;
import com.moyeorock.domain.auth.dto.request.KakaoSignupRequest;
import com.moyeorock.domain.auth.dto.request.UserLoginRequest;
import com.moyeorock.domain.auth.dto.request.UserSignupRequest;
import com.moyeorock.domain.auth.dto.response.AuthTokenResponse;
import com.moyeorock.domain.auth.dto.response.KakaoAuthResponse;
import com.moyeorock.domain.auth.service.KakaoSignupTokenProvider.SignupClaims;
import com.moyeorock.domain.user.entity.User;
import com.moyeorock.domain.user.enums.UserStatus;
import com.moyeorock.domain.user.repository.UserRepository;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import com.moyeorock.global.security.JwtProvider;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtProvider jwtProvider;
    @Mock KakaoOAuthClient kakaoOAuthClient;
    @Mock KakaoSignupTokenProvider signupTokenProvider;
    @Mock NicknameGenerator nicknameGenerator;
    @InjectMocks AuthService authService;

    private static final LocalDateTime AGREED_AT = LocalDateTime.of(2026, 9, 29, 10, 0);

    private static User emailUser(String nickname) {
        return User.signupWithEmail("seojun@example.com", "hashed", nickname, AGREED_AT);
    }

    private static User kakaoUser(String kakaoId, String nickname) {
        return User.signupWithKakao(kakaoId, nickname, AGREED_AT);
    }

    // 정지 상태를 만드는 도메인 메서드가 없어 필드를 직접 넣는다.
    private static User suspended(User user) {
        ReflectionTestUtils.setField(user, "status", UserStatus.SUSPENDED);
        return user;
    }

    private static User withId(User user, long id) {
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static ErrorCode errorCodeOf(Throwable thrown) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        return ((BusinessException) thrown).getErrorCode();
    }

    @Nested
    @DisplayName("signup")
    class Signup {

        private final UserSignupRequest request = new UserSignupRequest("seojun@example.com", "Passw0rd!", "서준", true);

        @Test
        @DisplayName("이메일 중복 검사 → 임시 닉네임 → 해시 저장 → 토큰 발급, onboardingCompleted=false")
        void signs_up_and_issues_token() {
            given(userRepository.existsByEmail("seojun@example.com")).willReturn(false);
            given(nicknameGenerator.resolve("서준")).willReturn("서준");
            given(passwordEncoder.encode("Passw0rd!")).willReturn("hashed");
            given(userRepository.saveAndFlush(any(User.class))).willAnswer(inv -> withId(inv.getArgument(0), 12L));
            given(jwtProvider.generateToken(12L)).willReturn("access");

            AuthTokenResponse response = authService.signup(request);

            ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
            verify(userRepository).saveAndFlush(saved.capture());
            assertThat(saved.getValue().getEmail()).isEqualTo("seojun@example.com");
            assertThat(saved.getValue().getPasswordHash()).isEqualTo("hashed");
            assertThat(saved.getValue().getPrivacyAgreedAt()).isNotNull();
            assertThat(saved.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(response).isEqualTo(new AuthTokenResponse("access", 12L, "서준", false));
        }

        @Test
        @DisplayName("이미 가입된 이메일이면 EMAIL_DUPLICATED, 저장하지 않는다")
        void duplicated_email() {
            given(userRepository.existsByEmail("seojun@example.com")).willReturn(true);

            assertThat(errorCodeOf(catchThrowable(() -> authService.signup(request)))).isEqualTo(ErrorCode.EMAIL_DUPLICATED);
            verify(userRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("동시 가입으로 UNIQUE에 걸리면 500 대신 EMAIL_DUPLICATED")
        void unique_violation_maps_to_409() {
            given(userRepository.existsByEmail(any())).willReturn(false);
            given(nicknameGenerator.resolve(any())).willReturn("서준");
            given(passwordEncoder.encode(any())).willReturn("hashed");
            given(userRepository.saveAndFlush(any(User.class))).willThrow(new DataIntegrityViolationException("dup"));

            assertThat(errorCodeOf(catchThrowable(() -> authService.signup(request)))).isEqualTo(ErrorCode.EMAIL_DUPLICATED);
        }
    }

    @Nested
    @DisplayName("login")
    class Login {

        private final UserLoginRequest request = new UserLoginRequest("seojun@example.com", "Passw0rd!");

        @Test
        @DisplayName("이메일·비밀번호가 맞고 ACTIVE면 토큰 발급, onboardingCompleted는 사용자 상태를 따른다")
        void logs_in() {
            User user = withId(emailUser("서준"), 12L);
            user.completeOnboarding("서준", null, null);
            given(userRepository.findByEmail("seojun@example.com")).willReturn(Optional.of(user));
            given(passwordEncoder.matches("Passw0rd!", "hashed")).willReturn(true);
            given(jwtProvider.generateToken(12L)).willReturn("access");

            assertThat(authService.login(request)).isEqualTo(new AuthTokenResponse("access", 12L, "서준", true));
        }

        @Test
        @DisplayName("이메일 없음·비밀번호 불일치·카카오 전용·탈퇴/정지는 전부 LOGIN_FAILED 하나")
        void every_failure_is_login_failed() {
            // 이메일 없음
            given(userRepository.findByEmail("seojun@example.com")).willReturn(Optional.empty());
            assertThat(errorCodeOf(catchThrowable(() -> authService.login(request)))).isEqualTo(ErrorCode.LOGIN_FAILED);

            // 카카오 전용 계정 (passwordHash null)
            given(userRepository.findByEmail("seojun@example.com")).willReturn(Optional.of(
                    withId(kakaoUser("k", "a"), 1L)));
            assertThat(errorCodeOf(catchThrowable(() -> authService.login(request)))).isEqualTo(ErrorCode.LOGIN_FAILED);
            verify(passwordEncoder, never()).matches(any(), any());

            // 비밀번호 불일치
            given(userRepository.findByEmail("seojun@example.com")).willReturn(Optional.of(
                    withId(emailUser("a"), 1L)));
            given(passwordEncoder.matches("Passw0rd!", "hashed")).willReturn(false);
            assertThat(errorCodeOf(catchThrowable(() -> authService.login(request)))).isEqualTo(ErrorCode.LOGIN_FAILED);

            // 정지 계정
            given(userRepository.findByEmail("seojun@example.com")).willReturn(Optional.of(
                    withId(suspended(emailUser("a")), 1L)));
            given(passwordEncoder.matches("Passw0rd!", "hashed")).willReturn(true);
            assertThat(errorCodeOf(catchThrowable(() -> authService.login(request)))).isEqualTo(ErrorCode.LOGIN_FAILED);
            verify(jwtProvider, never()).generateToken(any());
        }
    }

    @Nested
    @DisplayName("kakaoAuth")
    class KakaoAuth {

        @Test
        @DisplayName("기존 회원이면 isNewUser=false + 로그인 토큰, 계정을 만들지 않는다")
        void existing_user_logs_in() {
            given(kakaoOAuthClient.fetchUser("code")).willReturn(new KakaoUserInfo("k-1", "서준"));
            given(userRepository.findByKakaoId("k-1")).willReturn(Optional.of(
                    withId(kakaoUser("k-1", "서준"), 7L)));
            given(jwtProvider.generateToken(7L)).willReturn("access");

            KakaoAuthResponse response = authService.kakaoAuth("code");

            assertThat(response.isNewUser()).isFalse();
            assertThat(response.auth()).isEqualTo(new AuthTokenResponse("access", 7L, "서준", false));
            assertThat(response.signupToken()).isNull();
            verify(userRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("신규면 isNewUser=true + signupToken + 카카오 닉네임, 계정을 만들지 않는다")
        void new_user_gets_signup_token() {
            given(kakaoOAuthClient.fetchUser("code")).willReturn(new KakaoUserInfo("k-2", "민서"));
            given(userRepository.findByKakaoId("k-2")).willReturn(Optional.empty());
            given(signupTokenProvider.issue("k-2", "민서")).willReturn("signup-jwt");

            KakaoAuthResponse response = authService.kakaoAuth("code");

            assertThat(response.isNewUser()).isTrue();
            assertThat(response.signupToken()).isEqualTo("signup-jwt");
            assertThat(response.kakaoNickname()).isEqualTo("민서");
            assertThat(response.auth()).isNull();
            verify(userRepository, never()).saveAndFlush(any());
            verify(jwtProvider, never()).generateToken(any());
        }

        @Test
        @DisplayName("code가 비어 있으면 카카오를 호출하지 않고 VALIDATION_FAILED")
        void blank_code() {
            assertThat(errorCodeOf(catchThrowable(() -> authService.kakaoAuth(" ")))).isEqualTo(ErrorCode.VALIDATION_FAILED);
            verify(kakaoOAuthClient, never()).fetchUser(any());
        }

        @Test
        @DisplayName("정지된 카카오 회원은 LOGIN_FAILED")
        void suspended_kakao_user() {
            given(kakaoOAuthClient.fetchUser("code")).willReturn(new KakaoUserInfo("k-1", "서준"));
            given(userRepository.findByKakaoId("k-1")).willReturn(Optional.of(
                    withId(suspended(kakaoUser("k-1", "서준")), 7L)));

            assertThat(errorCodeOf(catchThrowable(() -> authService.kakaoAuth("code")))).isEqualTo(ErrorCode.LOGIN_FAILED);
        }
    }

    @Nested
    @DisplayName("kakaoSignup")
    class KakaoSignup {

        private final KakaoSignupRequest request = new KakaoSignupRequest("signup-jwt", true);

        @Test
        @DisplayName("토큰의 kakaoId로 계정을 만들고(email null) 토큰을 발급한다")
        void creates_kakao_user() {
            given(signupTokenProvider.parse("signup-jwt")).willReturn(new SignupClaims("k-2", "민서"));
            given(userRepository.findByKakaoId("k-2")).willReturn(Optional.empty());
            given(nicknameGenerator.resolve("민서")).willReturn("민서_a1b2");
            given(userRepository.saveAndFlush(any(User.class))).willAnswer(inv -> withId(inv.getArgument(0), 30L));
            given(jwtProvider.generateToken(30L)).willReturn("access");

            AuthTokenResponse response = authService.kakaoSignup(request);

            ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
            verify(userRepository).saveAndFlush(saved.capture());
            assertThat(saved.getValue().getKakaoId()).isEqualTo("k-2");
            assertThat(saved.getValue().getEmail()).isNull();
            assertThat(saved.getValue().getPasswordHash()).isNull();
            assertThat(response).isEqualTo(new AuthTokenResponse("access", 30L, "민서_a1b2", false));
        }

        @Test
        @DisplayName("이미 가입된 kakaoId면 ALREADY_REGISTERED (토큰 재사용)")
        void already_registered() {
            given(signupTokenProvider.parse("signup-jwt")).willReturn(new SignupClaims("k-1", "서준"));
            given(userRepository.findByKakaoId("k-1")).willReturn(Optional.of(kakaoUser("k-1", "서준")));

            assertThat(errorCodeOf(catchThrowable(() -> authService.kakaoSignup(request)))).isEqualTo(ErrorCode.ALREADY_REGISTERED);
            verify(userRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("토큰이 무효하면 provider의 INVALID_SIGNUP_TOKEN이 그대로 전파된다")
        void invalid_token() {
            given(signupTokenProvider.parse("signup-jwt")).willThrow(new BusinessException(ErrorCode.INVALID_SIGNUP_TOKEN));

            assertThat(errorCodeOf(catchThrowable(() -> authService.kakaoSignup(request)))).isEqualTo(ErrorCode.INVALID_SIGNUP_TOKEN);
        }
    }
}

package com.moyeorock.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.moyeorock.domain.user.enums.LoginType;
import com.moyeorock.domain.user.enums.UserStatus;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Region;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserTest {

    private static final LocalDateTime PRIVACY_AGREED_AT = LocalDateTime.of(2026, 9, 1, 0, 0);

    @Test
    @DisplayName("이메일 가입은 kakaoId가 없어 loginType이 EMAIL이다")
    void signupWithEmail_hasLoginTypeEmail() {
        User user = User.signupWithEmail("a@b.com", "hash", "서준", PRIVACY_AGREED_AT);

        assertThat(user.loginType()).isEqualTo(LoginType.EMAIL);
        assertThat(user.getPrivacyAgreedAt()).isEqualTo(PRIVACY_AGREED_AT);
    }

    @Test
    @DisplayName("카카오 가입은 kakaoId가 있어 loginType이 KAKAO다")
    void signupWithKakao_hasLoginTypeKakao() {
        User user = User.signupWithKakao("kakao-1", "서준", PRIVACY_AGREED_AT);

        assertThat(user.loginType()).isEqualTo(LoginType.KAKAO);
    }

    @Test
    @DisplayName("온보딩을 다시 호출해도 최초 완료 시각은 바뀌지 않는다 (멱등)")
    void completeOnboarding_keepsFirstCompletedAt_onReinvocation() throws InterruptedException {
        User user = User.signupWithEmail("a@b.com", "hash", "임시", PRIVACY_AGREED_AT);
        user.completeOnboarding("서준", Region.SEOUL, List.of(Genre.ROCK));
        LocalDateTime firstCompletedAt = user.getOnboardingCompletedAt();
        Thread.sleep(10);

        user.completeOnboarding("서준밴드", Region.GYEONGGI, List.of(Genre.INDIE));

        assertThat(user.getOnboardingCompletedAt()).isEqualTo(firstCompletedAt);
        assertThat(user.getNickname()).isEqualTo("서준밴드");
        assertThat(user.getRegion()).isEqualTo(Region.GYEONGGI);
    }

    @Test
    @DisplayName("updateProfile은 프로필·설정 필드를 전체 교체한다")
    void updateProfile_replacesAllFields() {
        User user = User.signupWithEmail("a@b.com", "hash", "서준", PRIVACY_AGREED_AT);

        user.updateProfile("서준밴드", Region.SEOUL, List.of(Genre.ROCK, Genre.INDIE),
                "베이스 3년차입니다.", "https://img", false, false);

        assertThat(user.getNickname()).isEqualTo("서준밴드");
        assertThat(user.getBio()).isEqualTo("베이스 3년차입니다.");
        assertThat(user.getProfileImage()).isEqualTo("https://img");
        assertThat(user.isRecommendable()).isFalse();
        assertThat(user.isActivityPublic()).isFalse();
    }

    @Test
    @DisplayName("탈퇴하면 이메일·카카오id·비밀번호가 비워지고 닉네임은 식별자로 바뀐다 (재가입 허용·개인정보 제거)")
    void withdraw_clearsPersonalInfo_andReplacesNickname() {
        User user = User.signupWithEmail("a@b.com", "hash", "서준", PRIVACY_AGREED_AT);

        user.withdraw();

        assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(user.getWithdrawnAt()).isNotNull();
        assertThat(user.getEmail()).isNull();
        assertThat(user.getKakaoId()).isNull();
        assertThat(user.getPasswordHash()).isNull();
        assertThat(user.getNickname()).startsWith("탈퇴회원_");
    }
}

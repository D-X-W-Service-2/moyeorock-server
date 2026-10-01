package com.moyeorock.domain.user.entity;

import com.moyeorock.domain.user.enums.LoginType;
import com.moyeorock.domain.user.enums.PlatformRole;
import com.moyeorock.domain.user.enums.UserStatus;
import com.moyeorock.global.common.entity.BaseEntity;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Region;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 255)
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "kakao_id", length = 64)
    private String kakaoId;

    @Column(length = 20, nullable = false)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private Region region;

    @Convert(converter = UserGenresConverter.class)
    @Column(columnDefinition = "json")
    private List<Genre> genres;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(name = "profile_image", length = 500)
    private String profileImage;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform_role", length = 10, nullable = false)
    private PlatformRole platformRole;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private UserStatus status;

    @Column(name = "is_recommendable")
    private boolean isRecommendable;

    @Column(name = "is_activity_public")
    private boolean isActivityPublic;

    @Column(name = "privacy_agreed_at", nullable = false)
    private LocalDateTime privacyAgreedAt;

    @Column(name = "onboarding_completed_at")
    private LocalDateTime onboardingCompletedAt;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    public static User signupWithEmail(String email, String passwordHash, String nickname,
            LocalDateTime privacyAgreedAt) {
        User user = new User();
        user.email = email;
        user.passwordHash = passwordHash;
        user.nickname = nickname;
        user.platformRole = PlatformRole.USER;
        user.status = UserStatus.ACTIVE;
        user.isRecommendable = true;
        user.isActivityPublic = true;
        user.privacyAgreedAt = privacyAgreedAt;
        return user;
    }

    public static User signupWithKakao(String kakaoId, String nickname, LocalDateTime privacyAgreedAt) {
        User user = new User();
        user.kakaoId = kakaoId;
        user.nickname = nickname;
        user.platformRole = PlatformRole.USER;
        user.status = UserStatus.ACTIVE;
        user.isRecommendable = true;
        user.isActivityPublic = true;
        user.privacyAgreedAt = privacyAgreedAt;
        return user;
    }

    public void completeOnboarding(String nickname, Region region, List<Genre> genres) {
        this.nickname = nickname;
        this.region = region;
        this.genres = genres;
        // 온보딩 재호출은 멱등(200)이다 — 이미 완료된 사용자가 다시 불러도 최초 완료 시각은 그대로 둔다.
        if (this.onboardingCompletedAt == null) {
            this.onboardingCompletedAt = LocalDateTime.now();
        }
    }

    public boolean isOnboardingCompleted() {
        return onboardingCompletedAt != null;
    }

    public void updateProfile(String nickname, Region region, List<Genre> genres, String bio,
            String profileImage, boolean recommendable, boolean activityPublic) {
        this.nickname = nickname;
        this.region = region;
        this.genres = genres;
        this.bio = bio;
        this.profileImage = profileImage;
        this.isRecommendable = recommendable;
        this.isActivityPublic = activityPublic;
    }

    public LoginType loginType() {
        return kakaoId != null ? LoginType.KAKAO : LoginType.EMAIL;
    }

    // email·kakaoId·passwordHash는 전부 UNIQUE라 null로 비우지 않으면 같은 계정으로 재가입이
    // 영구히 막힌다. nickname도 UNIQUE라 비워둘 수 없어 식별자로 대체한다(탈퇴자 개인정보 제거).
    public void withdraw() {
        this.status = UserStatus.WITHDRAWN;
        this.withdrawnAt = LocalDateTime.now();
        this.email = null;
        this.kakaoId = null;
        this.passwordHash = null;
        this.nickname = "탈퇴회원_" + this.id;
    }
}

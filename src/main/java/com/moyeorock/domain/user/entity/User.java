package com.moyeorock.domain.user.entity;

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
    @Column(name = "platform_role", length = 10)
    private PlatformRole platformRole;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
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

    public static User signupWithEmail(String email, String passwordHash, String nickname) {
        User user = new User();
        user.email = email;
        user.passwordHash = passwordHash;
        user.nickname = nickname;
        user.platformRole = PlatformRole.USER;
        user.status = UserStatus.ACTIVE;
        user.isRecommendable = true;
        user.isActivityPublic = true;
        user.privacyAgreedAt = LocalDateTime.now();
        return user;
    }

    public static User signupWithKakao(String kakaoId, String nickname) {
        User user = new User();
        user.kakaoId = kakaoId;
        user.nickname = nickname;
        user.platformRole = PlatformRole.USER;
        user.status = UserStatus.ACTIVE;
        user.isRecommendable = true;
        user.isActivityPublic = true;
        user.privacyAgreedAt = LocalDateTime.now();
        return user;
    }

    public void completeOnboarding(String nickname, Region region, List<Genre> genres) {
        this.nickname = nickname;
        this.region = region;
        this.genres = genres;
        this.onboardingCompletedAt = LocalDateTime.now();
    }

    public boolean isOnboardingCompleted() {
        return onboardingCompletedAt != null;
    }

    public void withdraw() {
        this.status = UserStatus.WITHDRAWN;
        this.withdrawnAt = LocalDateTime.now();
    }
}

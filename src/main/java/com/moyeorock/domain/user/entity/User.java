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
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자 (erd.md §1 users). created_at·updated_at 둘 다 있으므로 BaseEntity 상속.
 * 가입(행 생성)은 auth 도메인이 담당한다 — Phase 1에는 생성용 정적 팩토리가 없고,
 * 테스트 픽스처만 private 생성자의 @Builder를 쓴다 (conventions.md §1).
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    /** 탈퇴 시 치환되는 닉네임 접두사. 일반 닉네임에는 예약어로 금지한다. */
    public static final String WITHDRAWN_NICKNAME_PREFIX = "탈퇴회원";
    public static final int NICKNAME_MAX_LENGTH = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 255)
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "kakao_id", length = 64)
    private String kakaoId;

    @Column(nullable = false, length = NICKNAME_MAX_LENGTH)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private Region region;

    // JSON 컬럼. columnDefinition을 적어야 ddl-auto: validate가 varchar 대신 json으로 대조한다.
    @Convert(converter = GenreListConverter.class)
    @Column(columnDefinition = "json")
    private List<Genre> genres;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(name = "profile_image", length = 500)
    private String profileImage;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform_role", nullable = false, length = 10)
    private PlatformRole platformRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "is_recommendable", nullable = false)
    private boolean recommendable;

    @Column(name = "is_activity_public", nullable = false)
    private boolean activityPublic;

    @Column(name = "privacy_agreed_at", nullable = false)
    private LocalDateTime privacyAgreedAt;

    @Column(name = "onboarding_completed_at")
    private LocalDateTime onboardingCompletedAt;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    // 테스트 픽스처 전용. 프로덕션 생성은 auth 도메인의 정적 팩토리(추가 예정)로만 한다.
    @Builder
    private User(String email, String passwordHash, String kakaoId, String nickname, Region region,
                 List<Genre> genres, String bio, String profileImage, PlatformRole platformRole,
                 UserStatus status, Boolean recommendable, Boolean activityPublic,
                 LocalDateTime privacyAgreedAt, LocalDateTime onboardingCompletedAt) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.kakaoId = kakaoId;
        this.nickname = nickname;
        this.region = region;
        this.genres = genres == null ? null : new ArrayList<>(genres);
        this.bio = bio;
        this.profileImage = profileImage;
        this.platformRole = platformRole == null ? PlatformRole.USER : platformRole;
        this.status = status == null ? UserStatus.ACTIVE : status;
        this.recommendable = recommendable == null || recommendable;
        this.activityPublic = activityPublic == null || activityPublic;
        this.privacyAgreedAt = privacyAgreedAt == null ? LocalDateTime.now() : privacyAgreedAt;
        this.onboardingCompletedAt = onboardingCompletedAt;
    }

    /** PUT /users/me — 전체 교체. 선택 필드는 null이 오면 null로 덮어쓴다 (명세). 세션은 여기서 바꾸지 않는다. */
    public void updateProfile(String nickname, Region region, List<Genre> genres, String bio,
                              String profileImage, boolean recommendable, boolean activityPublic) {
        this.nickname = nickname;
        this.region = region;
        this.genres = genres == null ? null : new ArrayList<>(genres);
        this.bio = bio;
        this.profileImage = profileImage;
        this.recommendable = recommendable;
        this.activityPublic = activityPublic;
    }

    /** POST /users/me/onboarding — 재호출은 값만 덮어쓰고(멱등), 완료 시각은 처음 한 번만 찍는다. */
    public void completeOnboarding(String nickname, Region region, List<Genre> genres, LocalDateTime now) {
        this.nickname = nickname;
        this.region = region;
        this.genres = new ArrayList<>(GenreListConverter.emptyIfNull(genres));
        if (this.onboardingCompletedAt == null) {
            this.onboardingCompletedAt = now;
        }
    }

    /**
     * DELETE /users/me — 소프트 삭제. 행은 남기되 개인정보(email·kakaoId·passwordHash)는 비워
     * 같은 이메일·카카오 계정으로 재가입할 수 있게 한다 (2026-09-28 결정, user-api-초안-검토 LB-4).
     * 닉네임은 "탈퇴회원_{id}"로 치환해 다른 회원이 원래 닉네임을 쓸 수 있게 한다 (A-6).
     */
    public void withdraw(LocalDateTime now) {
        this.status = UserStatus.WITHDRAWN;
        this.withdrawnAt = now;
        this.nickname = WITHDRAWN_NICKNAME_PREFIX + "_" + id;
        this.email = null;
        this.kakaoId = null;
        this.passwordHash = null;
    }

    public LoginType loginType() {
        return kakaoId != null ? LoginType.KAKAO : LoginType.EMAIL;
    }

    public boolean isOnboardingCompleted() {
        return onboardingCompletedAt != null;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }
}

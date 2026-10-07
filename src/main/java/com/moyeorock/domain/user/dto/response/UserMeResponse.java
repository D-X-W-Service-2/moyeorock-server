package com.moyeorock.domain.user.dto.response;

import com.moyeorock.domain.user.entity.User;
import com.moyeorock.domain.user.entity.UserInstrument;
import com.moyeorock.domain.user.enums.LoginType;
import com.moyeorock.domain.user.enums.PlatformRole;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Region;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 본인 프로필. email·isRecommendable·isActivityPublic 같은 본인 전용 필드를 담는다 —
 * 타인 프로필(UserProfileResponse, Phase 2)에는 절대 담지 않는다 (dto-naming §2).
 */
public record UserMeResponse(
        Long id,
        String email,
        String nickname,
        Region region,
        List<Genre> genres,
        String bio,
        String profileImage,
        PlatformRole platformRole,
        LoginType loginType,
        List<UserInstrumentResponse> instruments,
        boolean isRecommendable,
        boolean isActivityPublic,
        boolean onboardingCompleted,
        LocalDateTime createdAt
) {

    public static UserMeResponse of(User user, List<UserInstrument> instruments) {
        return new UserMeResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getRegion(),
                user.getGenres(),
                user.getBio(),
                user.getProfileImage(),
                user.getPlatformRole(),
                user.loginType(),
                instruments.stream().map(UserInstrumentResponse::from).toList(),
                user.isRecommendable(),
                user.isActivityPublic(),
                user.isOnboardingCompleted(),
                user.getCreatedAt()
        );
    }
}

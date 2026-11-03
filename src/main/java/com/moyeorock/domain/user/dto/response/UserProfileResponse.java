package com.moyeorock.domain.user.dto.response;

import com.moyeorock.domain.user.entity.User;
import com.moyeorock.domain.user.entity.UserInstrument;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Level;
import com.moyeorock.global.common.enums.Region;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 타인 프로필(GET /v1/users/{id}). 본인 전용 필드(email·isRecommendable)는 담지 않는다(dto-naming §2).
 * isActivityPublic만은 담는다: 비공개면 activities가 null이라 프론트가 "비공개"와 "활동 없음"을 구분하려면 이 값이 필요하다(이슈 #90 P-3).
 * instruments는 UserInstrumentResponse를 재사용하지 않고 id 없는 중첩 record다(타인에게 내부 id를 노출하지 않는다, P-4).
 */
public record UserProfileResponse(
        Long id,
        String nickname,
        Region region,
        List<Genre> genres,
        String bio,
        String profileImage,
        List<ProfileInstrument> instruments,
        boolean isActivityPublic,
        UserActivityResponse activities,
        LocalDateTime createdAt
) {

    public record ProfileInstrument(Instrument instrument, Level level) {

        public static ProfileInstrument from(UserInstrument userInstrument) {
            return new ProfileInstrument(userInstrument.getInstrument(), userInstrument.getLevel());
        }
    }

    /** activities는 호출한 쪽이 공개 여부·조립 가능 여부를 판단해 넘긴다(비공개·2단계 전은 null). */
    public static UserProfileResponse of(User user, List<UserInstrument> instruments, UserActivityResponse activities) {
        return new UserProfileResponse(
                user.getId(),
                user.getNickname(),
                user.getRegion(),
                user.getGenres(),
                user.getBio(),
                user.getProfileImage(),
                instruments.stream().map(ProfileInstrument::from).toList(),
                user.isActivityPublic(),
                activities,
                user.getCreatedAt()
        );
    }
}

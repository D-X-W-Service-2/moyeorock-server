package com.moyeorock.domain.user.dto.response;

import com.moyeorock.domain.user.entity.User;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Region;
import java.util.List;

/**
 * 사용자 요약 + 활동 지역·장르. 북마크 대상 요약처럼 카드 형태로 사용자를 보여주는 곳이 쓴다
 * ({@code UserService.getProfileSummaries}). 전 팀 공용 {@link UserSummaryResponse}(id·닉네임·프로필 이미지)는
 * 그대로 두고 필드를 늘리지 않으려고 따로 만들었다(dto-naming.md §12).
 * 온보딩 전 사용자는 region·genres가 null일 수 있다.
 */
public record UserProfileSummaryResponse(
        Long id,
        String nickname,
        Region region,
        List<Genre> genres,
        String profileImage
) {

    public static UserProfileSummaryResponse from(User user) {
        return new UserProfileSummaryResponse(
                user.getId(), user.getNickname(), user.getRegion(), user.getGenres(), user.getProfileImage());
    }
}

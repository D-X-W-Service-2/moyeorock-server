package com.moyeorock.domain.user.dto.response;

import com.moyeorock.domain.user.entity.User;

/**
 * 사용자 요약. team·join·invitation·recruit 등 전 팀이 재사용한다 (dto-naming §12 재사용 1위).
 * 필드 변경 시 팀 공유 필수. dto-spec 상단 1팀 확정안 (id, nickname, profileImage) 그대로.
 */
public record UserSummaryResponse(Long id, String nickname, String profileImage) {

    public static UserSummaryResponse from(User user) {
        return new UserSummaryResponse(user.getId(), user.getNickname(), user.getProfileImage());
    }
}

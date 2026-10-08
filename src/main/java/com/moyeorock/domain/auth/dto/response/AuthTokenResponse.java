package com.moyeorock.domain.auth.dto.response;

import com.moyeorock.domain.user.entity.User;

/**
 * 가입·로그인 공용 응답 (dto-naming §1). 리프레시 토큰은 없다.
 * onboardingCompleted로 프론트가 추가 호출 없이 온보딩 화면/대시보드를 분기한다.
 */
public record AuthTokenResponse(String accessToken, Long userId, String nickname, boolean onboardingCompleted) {

    public static AuthTokenResponse of(User user, String accessToken) {
        return new AuthTokenResponse(accessToken, user.getId(), user.getNickname(), user.isOnboardingCompleted());
    }
}

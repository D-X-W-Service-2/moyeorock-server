package com.moyeorock.domain.user.dto.request;

import com.moyeorock.domain.user.entity.User;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Region;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * PUT /v1/users/me — 전체 교체. 선택 필드(region·genres·bio·profileImage)를 빼면 null로 덮어쓴다.
 * 세션은 이 요청으로 바꾸지 않는다 (PUT /v1/users/me/instruments).
 */
public record UserUpdateRequest(
        @NotBlank @Size(max = User.NICKNAME_MAX_LENGTH) String nickname,
        Region region,
        List<Genre> genres,
        String bio,
        @Size(max = 500) String profileImage,
        @NotNull Boolean isRecommendable,
        @NotNull Boolean isActivityPublic
) {
}

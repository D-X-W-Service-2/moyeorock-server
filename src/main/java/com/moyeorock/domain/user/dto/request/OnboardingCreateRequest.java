package com.moyeorock.domain.user.dto.request;

import com.moyeorock.domain.user.entity.User;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Region;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** POST /v1/users/me/onboarding — 전부 필수. genres는 빈 배열 허용, instruments는 1개 이상. */
public record OnboardingCreateRequest(
        @NotBlank @Size(max = User.NICKNAME_MAX_LENGTH) String nickname,
        @NotNull Region region,
        @NotNull List<Genre> genres,
        @NotEmpty List<@Valid UserInstrumentRequest> instruments
) {
}

package com.moyeorock.domain.user.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** PUT /v1/users/me/instruments — 전체 교체. 요청에 없는 세션은 삭제된다. */
public record UserInstrumentUpdateRequest(
        @NotEmpty List<@Valid UserInstrumentRequest> instruments
) {
}

package com.moyeorock.domain.join.dto.request;

import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.TargetType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InvitationCreateRequest(
        @NotNull TargetType targetType,
        @NotNull Long targetId,
        @NotNull Long inviteeId,
        Instrument instrument,
        @Size(max = 500) String message
) {
}

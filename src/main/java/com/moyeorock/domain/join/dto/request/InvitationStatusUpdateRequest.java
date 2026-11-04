package com.moyeorock.domain.join.dto.request;

import com.moyeorock.domain.join.enums.InvitationDecision;
import jakarta.validation.constraints.NotNull;

public record InvitationStatusUpdateRequest(@NotNull InvitationDecision status) {
}

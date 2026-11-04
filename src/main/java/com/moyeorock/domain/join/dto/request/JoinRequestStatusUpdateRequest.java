package com.moyeorock.domain.join.dto.request;

import com.moyeorock.global.common.enums.JoinStatus;
import jakarta.validation.constraints.NotNull;

// 허용 값은 APPROVED·REJECTED·CANCELED다. PENDING은 전이 대상이 아니라 Service가 400으로 막는다.
public record JoinRequestStatusUpdateRequest(@NotNull JoinStatus status) {
}

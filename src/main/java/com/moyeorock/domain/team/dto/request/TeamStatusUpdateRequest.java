package com.moyeorock.domain.team.dto.request;

import com.moyeorock.domain.team.enums.TeamStatus;
import jakarta.validation.constraints.NotNull;

// 사실상 DISBANDED만 허용한다. 값 검증은 Service에서 한다.
public record TeamStatusUpdateRequest(@NotNull TeamStatus status) {
}

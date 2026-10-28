package com.moyeorock.domain.recruit.dto.request;

import com.moyeorock.domain.recruit.enums.RecruitStatus;
import jakarta.validation.constraints.NotNull;

public record RecruitPostStatusUpdateRequest(@NotNull RecruitStatus status) {
}

package com.moyeorock.domain.recruit.dto.request;

import com.moyeorock.global.common.enums.Region;
import com.moyeorock.global.common.enums.TargetType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record RecruitPostCreateRequest(
        @NotNull TargetType targetType,
        @NotNull Long targetId,
        @NotBlank @Size(max = 100) String title,
        @NotBlank String body,
        @NotEmpty @Valid List<WantedSlotRequest> wantedSlots,
        @NotNull Region region
) {
}

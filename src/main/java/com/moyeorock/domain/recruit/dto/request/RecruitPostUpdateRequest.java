package com.moyeorock.domain.recruit.dto.request;

import com.moyeorock.global.common.enums.Region;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

// targetType·targetId 없음 — 모집 대상(팀/모임) 변경은 지원하지 않는다(dto-naming.md §5).
public record RecruitPostUpdateRequest(
        @NotBlank @Size(max = 100) String title,
        @NotBlank String body,
        @NotEmpty @Valid List<WantedSlotRequest> wantedSlots,
        @NotNull Region region
) {
}

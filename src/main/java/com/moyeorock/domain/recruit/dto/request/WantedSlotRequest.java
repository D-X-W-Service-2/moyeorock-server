package com.moyeorock.domain.recruit.dto.request;

import com.moyeorock.global.common.enums.Instrument;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record WantedSlotRequest(
        @NotNull Instrument instrument,
        @Min(1) int count
) {
}

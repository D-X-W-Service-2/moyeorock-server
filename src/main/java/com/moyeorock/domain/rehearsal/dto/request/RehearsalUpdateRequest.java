package com.moyeorock.domain.rehearsal.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record RehearsalUpdateRequest(
        @NotBlank @Size(max = 100) String title,
        @NotNull LocalDateTime startsAt,
        @NotNull LocalDateTime endsAt,
        @Size(max = 100) String place,
        String memo
        ) {
}

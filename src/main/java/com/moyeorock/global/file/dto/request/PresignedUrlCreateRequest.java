package com.moyeorock.global.file.dto.request;

import com.moyeorock.global.file.FileDomain;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PresignedUrlCreateRequest(
        @NotNull FileDomain domain,
        @NotBlank String fileName,
        @NotBlank String contentType
) {
}

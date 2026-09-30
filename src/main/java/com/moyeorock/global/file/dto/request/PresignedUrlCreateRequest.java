package com.moyeorock.global.file.dto.request;

import com.moyeorock.global.file.enums.UploadDomain;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PresignedUrlCreateRequest(
        @NotNull(message = "업로드 도메인은 필수입니다.")
        UploadDomain domain,

        @NotBlank(message = "파일명은 필수입니다.")
        String fileName,

        @NotBlank(message = "contentType은 필수입니다.")
        String contentType
) {
}

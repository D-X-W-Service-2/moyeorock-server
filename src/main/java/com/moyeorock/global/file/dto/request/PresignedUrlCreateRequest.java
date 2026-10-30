package com.moyeorock.global.file.dto.request;

import com.moyeorock.global.file.enums.UploadDomain;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PresignedUrlCreateRequest(
        @NotNull(message = "업로드 도메인은 필수입니다.")
        UploadDomain domain,

        // files.original_name이 VARCHAR(255)다. 상한이 없으면 255자를 넘겼을 때 MySQL이
        // 저장 단계에서 거부해 400이어야 할 입력 오류가 500으로 나간다.
        @NotBlank(message = "파일명은 필수입니다.")
        @Size(max = 255, message = "파일명은 255자 이하여야 합니다.")
        String fileName,

        // contentType은 files.content_type이 VARCHAR(100)이지만 FileService가 허용 목록
        // (image/jpeg·png·webp)과 정확히 일치하는지 먼저 검사해 길이 초과가 DB까지 가지 않는다.
        @NotBlank(message = "contentType은 필수입니다.")
        String contentType
) {
}

package com.moyeorock.global.file.dto.request;

import com.moyeorock.global.file.FileDomain;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record PresignedUrlCreateRequest(
        @NotNull FileDomain domain,
        @NotBlank String fileName,
        // text/html·image/svg+xml 등이 서명되면 우리 도메인에서 스크립트가 실행될 수 있어(stored XSS)
        // 이미지 3종만 허용한다 — 목록 확정은 이슈 #18 팀 결정 5번
        @NotBlank @Pattern(regexp = "image/(jpeg|png|gif)", message = "허용되지 않는 contentType입니다 (image/jpeg·image/png·image/gif)")
        String contentType
) {
}

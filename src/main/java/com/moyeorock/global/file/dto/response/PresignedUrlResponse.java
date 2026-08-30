package com.moyeorock.global.file.dto.response;

import java.time.LocalDateTime;

public record PresignedUrlResponse(
        String uploadUrl,
        String fileUrl,
        LocalDateTime expiresAt
) {
}

package com.moyeorock.global.file.client;

import java.time.LocalDateTime;

// uploadUrl: 클라이언트가 실제로 PUT할 임시 주소. fileUrl: 업로드 완료 후 영구 접근 주소
// (CDN 안 씀 — 스토리지가 직접 내려주는 주소를 그대로 쓴다. docs/plans/file-upload-presigned-url.md 참고).
public record UploadUrl(String uploadUrl, String fileUrl, LocalDateTime expiresAt) {
}

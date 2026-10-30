package com.moyeorock.global.file.dto.response;

import com.moyeorock.global.file.client.UploadUrl;
import com.moyeorock.global.file.entity.UploadedFile;
import java.time.LocalDateTime;

// dto-spec.md §10 예시엔 fileId가 없지만, DELETE /v1/files/{fileId}가 이 값을 요구하므로
// 넣지 않으면 클라이언트가 삭제를 호출할 방법이 없다 — 예시가 갱신 안 된 것으로 보고 추가했다.
public record PresignedUrlResponse(Long fileId, String uploadUrl, String fileUrl, LocalDateTime expiresAt) {

    public static PresignedUrlResponse of(UploadedFile file, UploadUrl uploadUrl) {
        return new PresignedUrlResponse(file.getId(), uploadUrl.uploadUrl(), uploadUrl.fileUrl(), uploadUrl.expiresAt());
    }
}

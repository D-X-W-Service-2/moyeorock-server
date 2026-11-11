package com.moyeorock.global.file.dto.response;

import com.moyeorock.global.file.client.UploadUrl;
import com.moyeorock.global.file.entity.UploadedFile;
import java.time.LocalDateTime;

// fileId는 처음에 DELETE /v1/files/{fileId}를 부르려고 넣었다. 삭제가 fileUrl 기준으로 바뀌어(이슈 #164) 지금은
// 삭제에 쓰지 않지만, 이미 이 값을 읽는 프론트 코드가 깨지지 않게 남겨 둔다. 삭제 응답의 id와 같은 값이다.
public record PresignedUrlResponse(Long fileId, String uploadUrl, String fileUrl, LocalDateTime expiresAt) {

    public static PresignedUrlResponse of(UploadedFile file, UploadUrl uploadUrl) {
        return new PresignedUrlResponse(file.getId(), uploadUrl.uploadUrl(), uploadUrl.fileUrl(), uploadUrl.expiresAt());
    }
}

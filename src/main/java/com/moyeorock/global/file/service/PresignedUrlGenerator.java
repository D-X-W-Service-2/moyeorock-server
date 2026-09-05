package com.moyeorock.global.file.service;

import java.time.Duration;

/**
 * 스토리지에 presigned 업로드 URL을 요청하는 접점.
 * FileService·컨트롤러가 AWS SDK 없이 컴파일·테스트되도록 S3 호출부만 분리한다 (구현체: S3PresignedUrlGenerator).
 */
public interface PresignedUrlGenerator {

    Result issue(String fileKey, String contentType, Duration expiration);

    record Result(String uploadUrl, String fileUrl) {
    }
}

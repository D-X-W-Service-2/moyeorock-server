package com.moyeorock.global.file.service;

import java.time.Duration;

/**
 * 스토리지에 presigned 업로드 URL을 요청하는 접점.
 * S3 연동(AWS SDK 의존성)은 PR #12와의 build.gradle 충돌을 피해 후속 커밋에서 구현체로 추가한다.
 */
public interface PresignedUrlGenerator {

    Result issue(String fileKey, String contentType, Duration expiration);

    record Result(String uploadUrl, String fileUrl) {
    }
}

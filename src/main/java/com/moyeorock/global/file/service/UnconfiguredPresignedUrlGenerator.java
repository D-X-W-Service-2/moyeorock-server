package com.moyeorock.global.file.service;

import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * S3 구현체가 들어오기 전까지 컨텍스트 기동을 위해 두는 임시 빈.
 * AWS SDK 의존성 추가(build.gradle)가 PR #12와 겹쳐 후속 커밋으로 미뤄진 상태 — 구현체가 들어오면 삭제한다.
 */
@Component
public class UnconfiguredPresignedUrlGenerator implements PresignedUrlGenerator {

    @Override
    public Result issue(String fileKey, String contentType, Duration expiration) {
        throw new UnsupportedOperationException("스토리지가 아직 연동되지 않았습니다 (이슈 #18 — S3 구현체 대기)");
    }
}

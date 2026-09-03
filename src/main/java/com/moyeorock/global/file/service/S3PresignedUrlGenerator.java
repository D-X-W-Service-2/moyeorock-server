package com.moyeorock.global.file.service;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;

/**
 * AWS 자격증명은 SDK 기본 체인(환경변수 AWS_ACCESS_KEY_ID·AWS_SECRET_ACCESS_KEY 등)으로 읽는다.
 * 서명은 로컬 연산이라 자격증명이 없어도 부팅은 되고, 발급 시점에만 실패한다.
 */
@Component
public class S3PresignedUrlGenerator implements PresignedUrlGenerator {

    private final S3Presigner presigner;
    private final S3Properties properties;

    @Autowired
    public S3PresignedUrlGenerator(S3Properties properties) {
        this(createPresigner(properties.region()), properties);
    }

    S3PresignedUrlGenerator(S3Presigner presigner, S3Properties properties) {
        this.presigner = presigner;
        this.properties = properties;
    }

    // region이 없으면 presigner 없이 부팅한다 — bucket과 마찬가지로 발급 시점에 실패시키기 위함
    // (테스트 등 main application.yml의 ${AWS_REGION:...} 기본값이 안 실리는 환경에서 컨텍스트가 죽지 않도록)
    private static S3Presigner createPresigner(String region) {
        if (region == null || region.isBlank()) {
            return null;
        }
        return S3Presigner.builder().region(Region.of(region)).build();
    }

    @Override
    public Result issue(String fileKey, String contentType, Duration expiration) {
        if (presigner == null) {
            throw new IllegalStateException(
                    "file.s3.region이 설정되지 않았습니다 — AWS_REGION 환경변수를 확인하세요");
        }
        if (properties.bucket() == null || properties.bucket().isBlank()) {
            throw new IllegalStateException(
                    "file.s3.bucket이 설정되지 않았습니다 — S3_BUCKET 환경변수를 확인하세요 (버킷 확정: 이슈 #18)");
        }
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(fileKey)
                .contentType(contentType)
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(expiration)
                .putObjectRequest(putObjectRequest)
                .build();
        String uploadUrl = presigner.presignPutObject(presignRequest).url().toString();
        return new Result(uploadUrl, buildFileUrl(fileKey));
    }

    private String buildFileUrl(String fileKey) {
        String publicBaseUrl = properties.publicBaseUrl();
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            return "https://%s.s3.%s.amazonaws.com/%s"
                    .formatted(properties.bucket(), properties.region(), fileKey);
        }
        return publicBaseUrl.replaceAll("/+$", "") + "/" + fileKey;
    }

    @PreDestroy
    void close() {
        if (presigner != null) {
            presigner.close();
        }
    }
}

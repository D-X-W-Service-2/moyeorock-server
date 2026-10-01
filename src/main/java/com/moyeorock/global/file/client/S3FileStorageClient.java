package com.moyeorock.global.file.client;

import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

// S3 전환 시 활성화되는 구현체("AWS 유료라 로컬 먼저" 결정에 따라 지금은 file.storage.type이
// s3일 때만 뜬다). CDN은 안 쓰기로 했으므로(docs/plans/file-upload-presigned-url.md) fileUrl은
// 버킷이 퍼블릭 읽기로 열려 있다는 전제로 직접 조립한다 — 버킷 정책 설정은 이 코드 밖의 일이다.
// S3FileStorageProperties의 @EnableConfigurationProperties는 S3StorageConfig가 이미 등록한다
// (같은 조건이라 여기서 또 선언 안 함).
@Component
@ConditionalOnProperty(prefix = "file.storage", name = "type", havingValue = "s3")
@RequiredArgsConstructor
public class S3FileStorageClient implements FileStorageClient {

    private final S3FileStorageProperties properties;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Override
    public UploadUrl issuePresignedUrl(String fileKey, String contentType) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(fileKey)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(properties.uploadUrlExpirySeconds()))
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
        LocalDateTime expiresAt = LocalDateTime.ofInstant(presignedRequest.expiration(), ZoneId.systemDefault());
        String fileUrl = "https://%s.s3.%s.amazonaws.com/%s".formatted(properties.bucket(), properties.region(), fileKey);

        return new UploadUrl(presignedRequest.url().toString(), fileUrl, expiresAt);
    }

    @Override
    public void write(String fileKey, InputStream content) {
        throw new UnsupportedOperationException("S3는 클라이언트가 presigned URL로 직접 업로드하므로 서버가 파일을 받지 않는다");
    }

    @Override
    public InputStream read(String fileKey) {
        throw new UnsupportedOperationException("S3는 fileUrl로 클라이언트가 직접 읽으므로 서버가 파일을 내려주지 않는다");
    }

    @Override
    public void delete(String fileKey) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(properties.bucket())
                .key(fileKey)
                .build());
    }
}

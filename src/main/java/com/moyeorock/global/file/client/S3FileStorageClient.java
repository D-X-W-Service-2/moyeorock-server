package com.moyeorock.global.file.client;

import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.OptionalLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

// file.storage.type이 s3일 때만 뜨는 구현체. CDN은 안 쓰기로 했으므로(docs/plans/file-upload-presigned-url.md)
// fileUrl은 S3 객체 주소를 직접 조립한다. 이미지 폴더(profile/·group/·performance/)는 버킷 정책으로 공개 읽기라
// fileUrl로 바로 열리지만, practice/(연습 녹음)는 비공개라 fileUrl은 식별자로만 쓰고 읽기는 issueReadUrl로 한다 —
// 버킷 정책 설정은 이 코드 밖의 일이다. S3FileStorageProperties의 @EnableConfigurationProperties는
// S3StorageConfig가 이미 등록한다(같은 조건이라 여기서 또 선언 안 함).
@Slf4j
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
                .signatureDuration(uploadUrlExpiry())
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
        LocalDateTime expiresAt = LocalDateTime.ofInstant(presignedRequest.expiration(), ZoneId.systemDefault());

        return new UploadUrl(presignedRequest.url().toString(), fileUrlPrefix() + fileKey, expiresAt);
    }

    @Override
    public Duration uploadUrlExpiry() {
        return Duration.ofSeconds(properties.uploadUrlExpirySeconds());
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

    // 서명만 하고 S3를 부르지 않는다. 만료 시간은 서명에 들어가 S3가 강제한다(EC2 역할의 임시 자격증명으로
    // 서명하면 그 자격증명이 먼저 만료될 때도 URL이 끊긴다 — 역할 자격증명은 수 시간이라 몇십 분 URL엔 문제없다).
    @Override
    public String issueReadUrl(String fileKey, Duration expiry) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(properties.bucket())
                .key(fileKey)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(expiry)
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    @Override
    public Optional<String> toFileKey(String fileUrl) {
        String prefix = fileUrlPrefix();
        if (fileUrl == null || !fileUrl.startsWith(prefix) || fileUrl.length() == prefix.length()) {
            return Optional.empty();
        }
        return Optional.of(fileUrl.substring(prefix.length()));
    }

    // 없는 객체에 HeadObject를 하면 버킷의 s3:ListBucket 권한이 있을 때만 404이고, 없으면 403이 온다(존재 여부를
    // 숨기려는 S3 동작). EC2 역할은 Put/Get/DeleteObject만 있어 없는 객체도 403이라, 둘 다 "아직 안 올라옴"으로 본다.
    // 실제 권한 문제와 구분이 안 되므로 403은 경고 로그를 남긴다.
    @Override
    public OptionalLong size(String fileKey) {
        try {
            return OptionalLong.of(s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(fileKey)
                    .build()).contentLength());
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return OptionalLong.empty();
            }
            if (e.statusCode() == 403) {
                log.warn("S3 HeadObject 403 — 객체가 없거나 권한이 없습니다(ListBucket 권한이 없으면 없는 객체도 403). fileKey={}",
                        fileKey);
                return OptionalLong.empty();
            }
            throw e;
        }
    }

    // issuePresignedUrl의 fileUrl과 toFileKey가 같은 규칙을 쓰도록 한 곳에서 만든다.
    private String fileUrlPrefix() {
        return "https://%s.s3.%s.amazonaws.com/".formatted(properties.bucket(), properties.region());
    }
}

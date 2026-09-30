package com.moyeorock.global.file.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

// 로컬 디스크 기반 기본 구현체 — "AWS 유료라 로컬 먼저" 팀 결정(이슈 #18) 때문에 기본값이다.
// S3로 옮길 때 이 클래스와 FileController의 로컬 업로드 수신·서빙 엔드포인트를 함께 걷어낸다.
@Component
@ConditionalOnProperty(prefix = "file.storage", name = "type", havingValue = "local", matchIfMissing = true)
@EnableConfigurationProperties(LocalFileStorageProperties.class)
@RequiredArgsConstructor
public class LocalFileStorageClient implements FileStorageClient {

    private static final String LOCAL_UPLOAD_PATH = "/v1/files/local-upload/";
    private static final String LOCAL_SERVE_PATH = "/v1/files/local/";

    private final LocalFileStorageProperties properties;

    @Override
    public UploadUrl issuePresignedUrl(String fileKey, String contentType) {
        String uploadUrl = properties.baseUrl() + LOCAL_UPLOAD_PATH + fileKey;
        String fileUrl = properties.baseUrl() + LOCAL_SERVE_PATH + fileKey;
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(properties.uploadUrlExpirySeconds());
        return new UploadUrl(uploadUrl, fileUrl, expiresAt);
    }

    @Override
    public void write(String fileKey, InputStream content) {
        try {
            Path path = resolvePath(fileKey);
            Files.createDirectories(path.getParent());
            Files.copy(content, path, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public InputStream read(String fileKey) {
        try {
            return Files.newInputStream(resolvePath(fileKey));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void delete(String fileKey) {
        try {
            Files.deleteIfExists(resolvePath(fileKey));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path resolvePath(String fileKey) {
        return Path.of(properties.basePath()).resolve(fileKey).normalize();
    }
}

package com.moyeorock.global.file.service;

import com.moyeorock.global.file.FileDomain;
import com.moyeorock.global.file.dto.request.PresignedUrlCreateRequest;
import com.moyeorock.global.file.dto.response.PresignedUrlResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
public class FileService {

    private final PresignedUrlGenerator presignedUrlGenerator;
    private final Duration expiration;

    public FileService(PresignedUrlGenerator presignedUrlGenerator,
                       @Value("${file.presigned-url-expiration-minutes:10}") long expirationMinutes) {
        this.presignedUrlGenerator = presignedUrlGenerator;
        this.expiration = Duration.ofMinutes(expirationMinutes);
    }

    public PresignedUrlResponse issuePresignedUrl(Long userId, PresignedUrlCreateRequest request) {
        String fileKey = createFileKey(request.domain(), request.fileName());
        LocalDateTime expiresAt = LocalDateTime.now().plus(expiration);
        PresignedUrlGenerator.Result result =
                presignedUrlGenerator.issue(fileKey, request.contentType(), expiration);
        // 발급 이력을 저장할 테이블이 아직 없어 로그로만 남긴다 — 누가 어떤 키를 받았는지 추적용
        log.info("presigned URL 발급: userId={}, fileKey={}", userId, fileKey);
        return new PresignedUrlResponse(result.uploadUrl(), result.fileUrl(), expiresAt);
    }

    private String createFileKey(FileDomain domain, String fileName) {
        return domain.getDirectory() + "/" + UUID.randomUUID() + extractExtension(fileName);
    }

    private String extractExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        String extension = fileName.substring(dotIndex + 1);
        // 영숫자 외 문자('/' 등)가 섞인 확장자를 키에 넣으면 도메인 디렉토리 아래에 의도치 않은 하위 경로가 생긴다
        if (!extension.matches("[a-zA-Z0-9]+")) {
            return "";
        }
        return "." + extension.toLowerCase();
    }
}

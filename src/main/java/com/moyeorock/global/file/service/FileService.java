package com.moyeorock.global.file.service;

import com.moyeorock.global.file.FileDomain;
import com.moyeorock.global.file.dto.request.PresignedUrlCreateRequest;
import com.moyeorock.global.file.dto.response.PresignedUrlResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class FileService {

    private final PresignedUrlGenerator presignedUrlGenerator;
    private final Duration expiration;

    public FileService(PresignedUrlGenerator presignedUrlGenerator,
                       @Value("${file.presigned-url-expiration-minutes:10}") long expirationMinutes) {
        this.presignedUrlGenerator = presignedUrlGenerator;
        this.expiration = Duration.ofMinutes(expirationMinutes);
    }

    public PresignedUrlResponse issuePresignedUrl(PresignedUrlCreateRequest request) {
        String fileKey = createFileKey(request.domain(), request.fileName());
        LocalDateTime expiresAt = LocalDateTime.now().plus(expiration);
        PresignedUrlGenerator.Result result =
                presignedUrlGenerator.issue(fileKey, request.contentType(), expiration);
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
        return fileName.substring(dotIndex).toLowerCase();
    }
}

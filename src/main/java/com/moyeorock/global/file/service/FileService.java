package com.moyeorock.global.file.service;

import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import com.moyeorock.global.file.client.FileStorageClient;
import com.moyeorock.global.file.client.UploadUrl;
import com.moyeorock.global.file.dto.response.PresignedUrlResponse;
import com.moyeorock.global.file.entity.UploadedFile;
import com.moyeorock.global.file.enums.UploadDomain;
import com.moyeorock.global.file.repository.UploadedFileRepository;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.NoSuchFileException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FileService {

    // 결정 필요 사항: 이미지 파일만 허용(docs/plans/file-upload-presigned-url.md). gif·heic 등이
    // 필요해지면 이 목록에 추가만 하면 된다.
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    // fileName 확장자만 막으면 contentType은 클라이언트가 원하는 값을 그대로 보낼 수 있어서
    // (예: fileName=x.png + contentType=text/html) GET /v1/files/local/**(인증 없음)가 그
    // contentType을 그대로 응답 헤더로 echo하면 저장형 XSS가 된다 — 확장자와 별개로 반드시 검증한다.
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    // receiveUpload는 raw PUT body를 그대로 디스크에 써서 spring.servlet.multipart.max-file-size가
    // 적용되지 않는다 — 로컬 디스크 고갈을 막기 위한 임시 상한(S3 전환 시 이 제약 자체가 사라진다).
    private static final long MAX_UPLOAD_SIZE_BYTES = 10L * 1024 * 1024;

    private static final Map<UploadDomain, String> FOLDER_BY_DOMAIN = Map.of(
            UploadDomain.PROFILE_IMAGE, "profile",
            UploadDomain.GROUP_COVER, "group",
            UploadDomain.PERFORMANCE_POSTER, "performance"
    );

    private final UploadedFileRepository uploadedFileRepository;
    private final FileStorageClient fileStorageClient;

    @Transactional
    public PresignedUrlResponse issuePresignedUrl(UploadDomain domain, String fileName, String contentType,
            Long uploaderId) {
        String extension = extractExtension(fileName);
        if (!ALLOWED_EXTENSIONS.contains(extension) || !isAllowedContentType(contentType)) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }

        String fileKey = generateFileKey(domain, extension);
        UploadUrl uploadUrl = fileStorageClient.issuePresignedUrl(fileKey, contentType);

        UploadedFile file = UploadedFile.create(fileKey, domain, fileName, contentType, uploaderId);
        UploadedFile saved = uploadedFileRepository.save(file);

        return PresignedUrlResponse.of(saved, uploadUrl);
    }

    @Transactional
    public void receiveUpload(String fileKey, InputStream content, Long userId) {
        UploadedFile file = getByFileKeyOrThrow(fileKey);
        if (!file.isUploadedBy(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        try {
            fileStorageClient.write(fileKey, new SizeLimitedInputStream(content, MAX_UPLOAD_SIZE_BYTES));
        } catch (UncheckedIOException e) {
            if (e.getCause() instanceof UploadSizeExceededException) {
                throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED);
            }
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public FileContent readLocalFile(String fileKey) {
        UploadedFile file = getByFileKeyOrThrow(fileKey);
        return new FileContent(openOrThrow(fileKey), file.getContentType());
    }

    @Transactional
    public void delete(Long fileId, Long userId) {
        UploadedFile file = uploadedFileRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));
        if (!file.isUploadedBy(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        // DB 삭제를 먼저 커밋 경로에 태우고 스토리지 삭제(비가역)를 나중에 한다 — 순서가 반대였다면
        // 스토리지 삭제 후 DB 커밋이 실패할 때 "파일은 없는데 DB row만 남는" 상태가 될 수 있었다.
        uploadedFileRepository.delete(file);
        fileStorageClient.delete(file.getFileKey());
    }

    private InputStream openOrThrow(String fileKey) {
        try {
            return fileStorageClient.read(fileKey);
        } catch (UncheckedIOException e) {
            if (e.getCause() instanceof NoSuchFileException) {
                throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
            }
            throw e;
        }
    }

    private UploadedFile getByFileKeyOrThrow(String fileKey) {
        return uploadedFileRepository.findByFileKey(fileKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));
    }

    private boolean isAllowedContentType(String contentType) {
        return ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT));
    }

    private String generateFileKey(UploadDomain domain, String extension) {
        String folder = FOLDER_BY_DOMAIN.get(domain);
        return folder + "/" + UUID.randomUUID() + "." + extension;
    }

    // 경로 조작(키 안에 "..", "/" 등을 넣는 것) 방지를 위해 확장자는 영숫자만 허용하고 소문자로 통일한다.
    private String extractExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex == -1 || dotIndex == fileName.length() - 1) {
            return "";
        }
        String extension = fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        return extension.matches("[a-z0-9]+") ? extension : "";
    }

    public record FileContent(InputStream content, String contentType) {
    }
}

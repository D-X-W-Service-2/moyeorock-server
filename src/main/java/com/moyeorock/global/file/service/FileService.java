package com.moyeorock.global.file.service;

import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import com.moyeorock.global.file.client.FileStorageClient;
import com.moyeorock.global.file.client.UploadUrl;
import com.moyeorock.global.file.dto.response.PresignedUrlResponse;
import com.moyeorock.global.file.entity.UploadedFile;
import com.moyeorock.global.file.enums.UploadDomain;
import com.moyeorock.global.file.repository.UploadedFileRepository;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.io.UncheckedIOException;
import java.nio.file.NoSuchFileException;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    // 도메인별 허용 형식·크기는 노션 API 초안 "Presigned URL 발급"의 표(2026-11-09)를 따른다.
    // 이미지 3개 도메인은 같은 규칙이고, 연습 녹음만 오디오다. 새 형식이 필요하면 해당 목록에 추가만 하면 된다.
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> AUDIO_EXTENSIONS = Set.of("mp3", "m4a", "wav");

    // fileName 확장자만 막으면 contentType은 클라이언트가 원하는 값을 그대로 보낼 수 있어서
    // (예: fileName=x.png + contentType=text/html) GET /v1/files/local/**(인증 없음)가 그
    // contentType을 그대로 응답 헤더로 echo하면 저장형 XSS가 된다 — 확장자와 별개로 반드시 검증한다.
    private static final Set<String> IMAGE_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> AUDIO_CONTENT_TYPES =
            Set.of("audio/mpeg", "audio/mp4", "audio/x-m4a", "audio/wav", "audio/x-wav");

    // 로컬 저장의 receiveUpload는 raw PUT body를 그대로 디스크에 써서 spring.servlet.multipart.max-file-size가
    // 적용되지 않아 여기서 막는다. S3는 클라이언트가 S3에 직접 올려 서버가 업로드 때 막을 수 없다 — 크기가
    // 중요한 쪽(연습 녹음)은 findSize·maxUploadBytes로 사후에 확인한다.
    private static final long IMAGE_MAX_BYTES = 10L * 1024 * 1024;
    private static final long AUDIO_MAX_BYTES = 100L * 1024 * 1024;

    private final UploadedFileRepository uploadedFileRepository;
    private final FileStorageClient fileStorageClient;

    @Transactional
    public PresignedUrlResponse issuePresignedUrl(UploadDomain domain, String fileName, String contentType,
            Long uploaderId) {
        UploadRule rule = ruleOf(domain);
        String extension = extractExtension(fileName);
        if (!rule.extensions().contains(extension) || !rule.allowsContentType(contentType)) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }

        String fileKey = rule.folder() + "/" + UUID.randomUUID() + "." + extension;
        UploadUrl uploadUrl = fileStorageClient.issuePresignedUrl(fileKey, contentType);

        UploadedFile file = UploadedFile.create(fileKey, domain, fileName, contentType, uploaderId);
        UploadedFile saved = uploadedFileRepository.save(file);

        return PresignedUrlResponse.of(saved, uploadUrl);
    }

    /**
     * 트랜잭션을 열지 않는다. 파일 쓰기에 걸리는 시간은 서버가 아니라 올리는 쪽 네트워크 속도로
     * 결정되는데, 트랜잭션을 걸면 그 시간 내내 DB 커넥션을 쥐고 있게 된다. 느린 업로드가 몇 건
     * 겹치면 커넥션 풀이 비어 파일과 무관한 API까지 멈춘다.
     *
     * <p>게다가 얻는 것도 없다 — DB 쓰기가 없고(조회 1번), 파일 시스템 쓰기는 롤백 대상이 아니라
     * 원자성도 만들어지지 않는다. 연관관계 매핑이 없어 영속성 컨텍스트도 필요 없다.
     *
     * <p>남는 경합: 권한 확인과 쓰기 사이에 같은 파일이 삭제되면 DB 행 없는 파일이 디스크에 남는다.
     * 평범한 조회는 행을 잠그지 않으므로 트랜잭션이 있어도 막히지 않던 문제이고, 막으려면 업로드가
     * 끝날 때까지 행 잠금을 들고 있어야 해서 더 나쁘다. S3 전환 시 이 경로 자체가 사라진다.
     */
    public void receiveUpload(String fileKey, String contentType, InputStream content, Long userId) {
        UploadedFile file = getByFileKeyOrThrow(fileKey);
        if (!file.isUploadedBy(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        // 업로더 확인 뒤에 둔다 — 남의 파일에 대해 발급 시각(만료 여부)을 알려주지 않으려고.
        // 만료 전 재업로드는 막지 않고 덮어쓴다 — S3 presigned URL도 만료 전엔 여러 번 PUT할 수 있다.
        if (file.isUploadExpired(fileStorageClient.uploadUrlExpiry())) {
            throw new BusinessException(ErrorCode.UPLOAD_URL_EXPIRED);
        }
        // S3는 서명된 Content-Type과 다르면 거부한다 — 로컬도 맞춘다. 특히 FormData(multipart)로
        // 보내면 Spring이 바디를 먼저 파싱해 버려서, 막지 않으면 빈 파일이 200으로 저장된다.
        if (contentType == null || !file.getContentType().equalsIgnoreCase(contentType)) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }
        InputStream body = requireNonEmpty(content);
        try {
            fileStorageClient.write(fileKey, new SizeLimitedInputStream(body, ruleOf(file.getDomain()).maxBytes()));
        } catch (UncheckedIOException e) {
            if (e.getCause() instanceof UploadSizeExceededException) {
                throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED);
            }
            throw e;
        }
    }

    /**
     * 쿼리가 메타데이터 조회 1번이고 나머지는 파일 I/O라 트랜잭션을 열지 않는다
     * (conventions.md §3 — 쿼리 2번 이상의 결과가 서로 맞아야 할 때만 readOnly를 붙인다).
     */
    public FileContent readLocalFile(String fileKey) {
        UploadedFile file = getByFileKeyOrThrow(fileKey);
        return new FileContent(openOrThrow(fileKey), file.getContentType());
    }

    /**
     * 다른 도메인이 요청으로 받은 {@code fileUrl}이 이 사용자가 {@code domain}으로 올린 파일인지 확인하고 스토리지
     * key를 돌려준다. 비공개 파일(연습 녹음)은 URL이 아니라 이 key를 저장해 두고, 읽을 때마다
     * {@link #issueReadUrl}로 임시 URL을 만든다.
     *
     * <p>이 저장소가 만든 URL이 아니거나, 발급 이력(files 행)이 없거나, 업로더·도메인이 다르면 빈 값이다 —
     * 어느 경우인지는 구분해 알려주지 않는다(남의 파일이 있는지 드러내지 않으려고). 쿼리 1번이라 트랜잭션은 열지 않는다.
     */
    public Optional<String> findOwnedFileKey(String fileUrl, UploadDomain domain, Long userId) {
        return fileStorageClient.toFileKey(fileUrl)
                .flatMap(uploadedFileRepository::findByFileKey)
                .filter(file -> file.isUploadedBy(userId) && file.getDomain() == domain)
                .map(UploadedFile::getFileKey);
    }

    /** 비공개 파일을 읽을 임시 URL. 권한 확인은 부르는 도메인이 key를 저장할 때(findOwnedFileKey) 끝낸 것으로 본다. */
    public String issueReadUrl(String fileKey, Duration expiry) {
        return fileStorageClient.issueReadUrl(fileKey, expiry);
    }

    /** 실제로 올라간 파일 크기(바이트). 아직 안 올라왔으면 빈 값. */
    public OptionalLong findSize(String fileKey) {
        return fileStorageClient.size(fileKey);
    }

    /** 도메인별 업로드 크기 한도. S3는 업로드 때 막지 못해 부르는 쪽이 findSize와 비교한다. */
    public long maxUploadBytes(UploadDomain domain) {
        return ruleOf(domain).maxBytes();
    }

    /**
     * 다른 도메인이 자기 데이터와 함께 지우는 파일을 key로 지운다(연습 녹음 — 회원 탈퇴, PR #157 리뷰). 부르는 쪽 트랜잭션에
     * 참여해 files 행을 지우고, 저장소 객체는 커밋된 뒤에 지운다(delete와 같은 순서 — 롤백되면 파일이 남는다).
     * 업로더가 다른 key와 이미 지워진 key는 건너뛴다(사용자가 파일 삭제 API로 먼저 지웠을 수 있다). 같은 key가 여러 번 와도 한 번만 지운다.
     */
    @Transactional
    public void deleteOwnedFiles(Long uploaderId, Collection<String> fileKeys) {
        if (fileKeys.isEmpty()) {
            return;
        }
        List<UploadedFile> files = uploadedFileRepository.findAllByFileKeyIn(Set.copyOf(fileKeys)).stream()
                .filter(file -> file.isUploadedBy(uploaderId))
                .toList();
        uploadedFileRepository.deleteAll(files);
        files.forEach(file -> deleteFromStorageAfterCommit(file.getFileKey()));
    }

    @Transactional
    public void delete(Long fileId, Long userId) {
        UploadedFile file = uploadedFileRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));
        if (!file.isUploadedBy(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        uploadedFileRepository.delete(file);
        deleteFromStorageAfterCommit(file.getFileKey());
    }

    // repository.delete()는 삭제를 예약만 하고 실제 DELETE는 커밋 때 나간다 — 그 전에 스토리지(비가역)를
    // 지우면 커밋이 실패했을 때 "파일은 없는데 DB 행만 남는" 상태가 된다. 그래서 커밋이 끝난 뒤에 지운다.
    // 이 시점의 실패는 이미 커밋된 삭제를 되돌릴 수 없으니 던지지 않고 로그만 남긴다 — 던지면 클라이언트는
    // 500을 받고 삭제가 실패한 줄 안다. 남는 건 어떤 행도 가리키지 않는 파일뿐이다.
    private void deleteFromStorageAfterCommit(String fileKey) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    fileStorageClient.delete(fileKey);
                } catch (RuntimeException e) {
                    log.warn("files 행은 삭제됐지만 스토리지 삭제에 실패했습니다. fileKey={}", fileKey, e);
                }
            }
        });
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

    // 바디가 비어 있으면 빈 파일이 정상 업로드처럼 저장된다. 청크 전송이면 Content-Length로 알 수
    // 없어서 첫 바이트를 직접 읽어 보고 되돌려 놓는다.
    private InputStream requireNonEmpty(InputStream content) {
        try {
            PushbackInputStream in = new PushbackInputStream(content);
            int first = in.read();
            if (first == -1) {
                throw new BusinessException(ErrorCode.EMPTY_FILE);
            }
            in.unread(first);
            return in;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private UploadedFile getByFileKeyOrThrow(String fileKey) {
        return uploadedFileRepository.findByFileKey(fileKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));
    }

    // default 없는 switch라 UploadDomain에 값이 늘면 규칙을 정하기 전까지 컴파일되지 않는다.
    private static UploadRule ruleOf(UploadDomain domain) {
        return switch (domain) {
            case PROFILE_IMAGE -> new UploadRule("profile", IMAGE_EXTENSIONS, IMAGE_CONTENT_TYPES, IMAGE_MAX_BYTES);
            case GROUP_COVER -> new UploadRule("group", IMAGE_EXTENSIONS, IMAGE_CONTENT_TYPES, IMAGE_MAX_BYTES);
            case PERFORMANCE_POSTER ->
                    new UploadRule("performance", IMAGE_EXTENSIONS, IMAGE_CONTENT_TYPES, IMAGE_MAX_BYTES);
            case PRACTICE_AUDIO -> new UploadRule("practice", AUDIO_EXTENSIONS, AUDIO_CONTENT_TYPES, AUDIO_MAX_BYTES);
        };
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

    // 업로드 도메인 하나의 저장 폴더·허용 형식·크기 한도.
    private record UploadRule(String folder, Set<String> extensions, Set<String> contentTypes, long maxBytes) {

        boolean allowsContentType(String contentType) {
            return contentTypes.contains(contentType.toLowerCase(Locale.ROOT));
        }
    }
}

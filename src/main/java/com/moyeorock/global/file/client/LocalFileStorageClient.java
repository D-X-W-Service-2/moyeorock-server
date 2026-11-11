package com.moyeorock.global.file.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.OptionalLong;
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

    // FileController가 이 경로들로 실제 업로드 수신·서빙 엔드포인트를 매핑한다 — 두 클래스가
    // 각자 하드코딩해서 어긋나는 걸 막기 위해 여기 하나만 정의하고 컨트롤러는 이 상수를 참조한다.
    public static final String LOCAL_UPLOAD_PATH = "/v1/files/local-upload/";
    public static final String LOCAL_SERVE_PATH = "/v1/files/local/";

    private final LocalFileStorageProperties properties;

    @Override
    public UploadUrl issuePresignedUrl(String fileKey, String contentType) {
        String uploadUrl = properties.baseUrl() + LOCAL_UPLOAD_PATH + fileKey;
        LocalDateTime expiresAt = LocalDateTime.now().plus(uploadUrlExpiry());
        return new UploadUrl(uploadUrl, fileUrlPrefix() + fileKey, expiresAt);
    }

    // 로컬 서빙(GET /v1/files/local/**)은 인증·만료가 없다. 로컬 저장은 개발용이라 비공개를 흉내 내지 않고
    // fileUrl을 그대로 준다 — 비공개 파일도 로컬에서는 이 경로로 열린다.
    @Override
    public String issueReadUrl(String fileKey, Duration expiry) {
        return fileUrlPrefix() + fileKey;
    }

    @Override
    public Optional<String> toFileKey(String fileUrl) {
        String prefix = fileUrlPrefix();
        if (fileUrl == null || !fileUrl.startsWith(prefix) || fileUrl.length() == prefix.length()) {
            return Optional.empty();
        }
        return Optional.of(fileUrl.substring(prefix.length()));
    }

    @Override
    public OptionalLong size(String fileKey) {
        try {
            Path path = resolvePath(fileKey);
            return Files.isRegularFile(path) ? OptionalLong.of(Files.size(path)) : OptionalLong.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Duration uploadUrlExpiry() {
        return Duration.ofSeconds(properties.uploadUrlExpirySeconds());
    }

    // 같은 디렉토리의 임시 파일에 끝까지 쓴 뒤 원자적으로 교체한다. Files.copy(REPLACE_EXISTING)로
    // 바로 쓰면 기존 파일을 먼저 지우고 쓰기 시작하는데, 중간에 실패(용량 초과·연결 끊김)해도 쓰다 만
    // 파일을 정리하지 않는다 — 잘린 파일이 fileUrl로 그대로 서빙되고, 재업로드 실패 시 기존 파일까지
    // 사라진다. 임시 파일은 files 테이블에 키가 없어 서빙 엔드포인트로 노출되지 않는다.
    @Override
    public void write(String fileKey, InputStream content) {
        try {
            Path path = resolvePath(fileKey);
            Files.createDirectories(path.getParent());
            Path temp = Files.createTempFile(path.getParent(), ".upload-", ".tmp");
            try {
                Files.copy(content, temp, StandardCopyOption.REPLACE_EXISTING);
                Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                deleteQuietly(temp, e);
                throw e;
            }
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
    public byte[] readAtMost(String fileKey, int maxBytes) {
        try (InputStream content = Files.newInputStream(resolvePath(fileKey))) {
            return content.readNBytes(maxBytes);
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

    // 원래 예외(UploadSizeExceededException 등)를 FileService가 원인으로 구분해야 하므로 정리 실패는
    // 덮어쓰지 않고 suppressed로만 붙인다.
    private void deleteQuietly(Path temp, IOException original) {
        try {
            Files.deleteIfExists(temp);
        } catch (IOException e) {
            original.addSuppressed(e);
        }
    }

    // issuePresignedUrl의 fileUrl과 toFileKey가 같은 규칙을 쓰도록 한 곳에서 만든다.
    private String fileUrlPrefix() {
        return properties.baseUrl() + LOCAL_SERVE_PATH;
    }

    // fileKey는 현재 항상 서버가 만든 값만 쓰이지만(FileService.issuePresignedUrl), 방어적으로
    // basePath 하위를 벗어나는 경로(예: "../../etc/passwd")는 여기서 한 번 더 막는다.
    private Path resolvePath(String fileKey) {
        Path base = Path.of(properties.basePath()).normalize();
        Path resolved = base.resolve(fileKey).normalize();
        if (!resolved.startsWith(base)) {
            throw new IllegalArgumentException("잘못된 파일 경로입니다: " + fileKey);
        }
        return resolved;
    }
}

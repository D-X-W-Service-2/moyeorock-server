package com.moyeorock.global.file.service;

import com.moyeorock.global.file.FileDomain;
import com.moyeorock.global.file.dto.request.PresignedUrlCreateRequest;
import com.moyeorock.global.file.dto.response.PresignedUrlResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FileServiceTest {

    private static final long EXPIRATION_MINUTES = 10;

    private RecordingPresignedUrlGenerator generator;
    private FileService fileService;

    @BeforeEach
    void setUp() {
        generator = new RecordingPresignedUrlGenerator();
        fileService = new FileService(generator, EXPIRATION_MINUTES);
    }

    @Test
    @DisplayName("파일 키는 도메인 디렉토리/UUID.확장자 형태로 생성된다")
    void fileKeyFormat() {
        issue(FileDomain.PERFORMANCE_POSTER, "poster.png");

        String[] parts = generator.lastFileKey.split("/");
        assertThat(parts).hasSize(2);
        assertThat(parts[0]).isEqualTo("performance");
        assertThat(parts[1]).endsWith(".png");
        String uuidPart = parts[1].substring(0, parts[1].length() - ".png".length());
        assertThat(UUID.fromString(uuidPart)).isNotNull();
    }

    @Test
    @DisplayName("확장자는 소문자로 통일되고, 없으면 키에 확장자가 붙지 않는다")
    void extensionNormalization() {
        issue(FileDomain.PROFILE_IMAGE, "ME.PNG");
        assertThat(generator.lastFileKey).startsWith("profile/").endsWith(".png");

        issue(FileDomain.GROUP_COVER, "coverimage");
        assertThat(generator.lastFileKey).startsWith("group/").doesNotContain(".");
    }

    @Test
    @DisplayName("확장자에 영숫자 외 문자가 섞이면 확장자를 버린다 — 키에 하위 경로 주입 방지")
    void rejectsUnsafeExtension() {
        issue(FileDomain.PROFILE_IMAGE, "a.png/evil");

        assertThat(generator.lastFileKey)
                .startsWith("profile/")
                .doesNotContain(".")
                .satisfies(key -> assertThat(key.chars().filter(c -> c == '/').count()).isEqualTo(1));
    }

    @Test
    @DisplayName("응답은 생성기의 URL 두 개와 설정된 만료 시각을 담는다")
    void responseFields() {
        LocalDateTime before = LocalDateTime.now().plusMinutes(EXPIRATION_MINUTES);
        PresignedUrlResponse response = issue(FileDomain.PERFORMANCE_POSTER, "poster.png");
        LocalDateTime after = LocalDateTime.now().plusMinutes(EXPIRATION_MINUTES);

        assertThat(response.uploadUrl()).isEqualTo("https://upload.example.com");
        assertThat(response.fileUrl()).isEqualTo("https://file.example.com");
        assertThat(response.expiresAt()).isBetween(before, after);
        assertThat(generator.lastExpiration).isEqualTo(Duration.ofMinutes(EXPIRATION_MINUTES));
        assertThat(generator.lastContentType).isEqualTo("image/png");
    }

    private PresignedUrlResponse issue(FileDomain domain, String fileName) {
        return fileService.issuePresignedUrl(new PresignedUrlCreateRequest(domain, fileName, "image/png"));
    }

    private static class RecordingPresignedUrlGenerator implements PresignedUrlGenerator {
        String lastFileKey;
        String lastContentType;
        Duration lastExpiration;

        @Override
        public Result issue(String fileKey, String contentType, Duration expiration) {
            this.lastFileKey = fileKey;
            this.lastContentType = contentType;
            this.lastExpiration = expiration;
            return new Result("https://upload.example.com", "https://file.example.com");
        }
    }
}

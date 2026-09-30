package com.moyeorock.global.file.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileStorageClientTest {

    @TempDir
    Path tempDir;

    private LocalFileStorageClient client(Path basePath) {
        LocalFileStorageProperties properties = new LocalFileStorageProperties(
                basePath.toString(), "http://localhost:8080", 600);
        return new LocalFileStorageClient(properties);
    }

    @Test
    @DisplayName("presigned URL 발급 시 uploadUrl·fileUrl이 base-url과 서로 다른 경로를 가리킨다")
    void issuePresignedUrl_returnsDistinctUploadAndFileUrls() {
        LocalFileStorageClient client = client(tempDir);

        UploadUrl uploadUrl = client.issuePresignedUrl("profile/abc.png", "image/png");

        assertThat(uploadUrl.uploadUrl()).isEqualTo("http://localhost:8080/v1/files/local-upload/profile/abc.png");
        assertThat(uploadUrl.fileUrl()).isEqualTo("http://localhost:8080/v1/files/local/profile/abc.png");
        assertThat(uploadUrl.expiresAt()).isAfter(java.time.LocalDateTime.now());
    }

    @Test
    @DisplayName("write로 저장한 파일을 read로 그대로 다시 읽을 수 있다")
    void write_thenRead_returnsSameBytes() throws Exception {
        LocalFileStorageClient client = client(tempDir);
        byte[] bytes = {1, 2, 3, 4};

        client.write("profile/abc.png", new ByteArrayInputStream(bytes));

        try (InputStream read = client.read("profile/abc.png")) {
            assertThat(read.readAllBytes()).isEqualTo(bytes);
        }
    }

    @Test
    @DisplayName("delete로 지우면 파일이 디스크에서 사라진다")
    void delete_removesFileFromDisk() throws Exception {
        LocalFileStorageClient client = client(tempDir);
        client.write("profile/abc.png", new ByteArrayInputStream(new byte[]{1}));

        client.delete("profile/abc.png");

        assertThat(Files.exists(tempDir.resolve("profile/abc.png"))).isFalse();
    }

    @Test
    @DisplayName("존재하지 않는 파일을 지워도 예외가 나지 않는다")
    void delete_doesNotThrow_whenFileMissing() {
        LocalFileStorageClient client = client(tempDir);

        client.delete("profile/never-existed.png");
    }
}

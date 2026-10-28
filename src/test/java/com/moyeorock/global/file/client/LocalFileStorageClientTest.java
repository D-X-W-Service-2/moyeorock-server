package com.moyeorock.global.file.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
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
    @DisplayName("쓰는 도중 실패하면 잘린 파일도 임시 파일도 남기지 않는다")
    void write_leavesNoFile_whenStreamFailsMidway() throws Exception {
        LocalFileStorageClient client = client(tempDir);

        assertThatThrownBy(() -> client.write("profile/abc.png", failingAfter(50_000)))
                .isInstanceOf(UncheckedIOException.class);

        assertThat(Files.exists(tempDir.resolve("profile/abc.png"))).isFalse();
        try (Stream<Path> files = Files.list(tempDir.resolve("profile"))) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    @DisplayName("기존 파일 위에 다시 쓰다 실패하면 기존 파일이 그대로 남는다")
    void write_keepsExistingFile_whenOverwriteFails() throws Exception {
        LocalFileStorageClient client = client(tempDir);
        byte[] original = {1, 2, 3};
        client.write("profile/abc.png", new ByteArrayInputStream(original));

        assertThatThrownBy(() -> client.write("profile/abc.png", failingAfter(50_000)))
                .isInstanceOf(UncheckedIOException.class);

        assertThat(Files.readAllBytes(tempDir.resolve("profile/abc.png"))).isEqualTo(original);
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

    @Test
    @DisplayName("fileKey에 \"..\"가 있어 basePath를 벗어나려 하면 예외를 던진다")
    void write_throws_whenFileKeyEscapesBasePath() {
        // fileKey는 지금 항상 서버가 생성하지만, 방어적으로 containment check가 실제로 동작하는지 검증한다.
        LocalFileStorageClient client = client(tempDir);

        assertThatThrownBy(() -> client.write("../outside.png", new ByteArrayInputStream(new byte[]{1})))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(Files.exists(tempDir.resolveSibling("outside.png"))).isFalse();
    }

    // 용량 초과·연결 끊김처럼 일부를 흘려보낸 뒤 예외가 나는 업로드 스트림
    private static InputStream failingAfter(int bytes) {
        return new FilterInputStream(new ByteArrayInputStream(new byte[bytes * 2])) {
            private int read;

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                if (read >= bytes) {
                    throw new IOException("업로드 중단");
                }
                int n = super.read(b, off, Math.min(len, bytes - read));
                read += n;
                return n;
            }
        };
    }
}

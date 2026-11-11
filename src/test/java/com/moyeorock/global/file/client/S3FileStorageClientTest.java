package com.moyeorock.global.file.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.NoSuchFileException;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * S3 구현체 단위 테스트. presigned URL은 서명만 하고 S3를 부르지 않으므로 테스트용 고정 자격증명으로 만든 실제
 * {@link S3Presigner}를 쓴다(네트워크 없음). HeadObject처럼 S3를 실제로 부르는 건 {@link S3Client}를 Mockito로 대신한다.
 */
class S3FileStorageClientTest {

    private static final String BUCKET = "moyeorock-test-files";
    private static final String OBJECT_URL_PREFIX = "https://moyeorock-test-files.s3.ap-northeast-2.amazonaws.com/";

    private S3Client s3Client;
    private S3Presigner s3Presigner;
    private S3FileStorageClient client;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        s3Presigner = S3Presigner.builder()
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("AKIATESTACCESSKEY", "test-secret-key")))
                .build();
        client = new S3FileStorageClient(
                new S3FileStorageProperties(BUCKET, "ap-northeast-2", 600), s3Client, s3Presigner);
    }

    @AfterEach
    void tearDown() {
        s3Presigner.close();
    }

    @Test
    @DisplayName("업로드 URL 발급의 fileUrl은 S3 객체 주소이고, toFileKey로 다시 fileKey가 된다")
    void issuePresignedUrl_fileUrl_roundTripsToFileKey() {
        UploadUrl uploadUrl = client.issuePresignedUrl("practice/abc.m4a", "audio/mp4");

        assertThat(uploadUrl.fileUrl()).isEqualTo(OBJECT_URL_PREFIX + "practice/abc.m4a");
        assertThat(client.toFileKey(uploadUrl.fileUrl())).contains("practice/abc.m4a");
    }

    @Test
    @DisplayName("다른 버킷·다른 리전·S3가 아닌 URL은 fileKey로 바꾸지 않는다")
    void toFileKey_returnsEmpty_forForeignUrl() {
        assertThat(client.toFileKey("https://other-bucket.s3.ap-northeast-2.amazonaws.com/practice/abc.m4a")).isEmpty();
        assertThat(client.toFileKey("https://moyeorock-test-files.s3.us-east-1.amazonaws.com/practice/abc.m4a"))
                .isEmpty();
        assertThat(client.toFileKey("http://localhost:8080/v1/files/local/practice/abc.m4a")).isEmpty();
        assertThat(client.toFileKey(OBJECT_URL_PREFIX)).isEmpty();
        assertThat(client.toFileKey(null)).isEmpty();
    }

    @Test
    @DisplayName("읽기 URL은 이 버킷의 객체를 가리키는 GET 서명 URL이고, 요청한 만료 시간이 서명에 들어간다")
    void issueReadUrl_signsGetUrl_withExpiry() {
        String readUrl = client.issueReadUrl("practice/abc.m4a", Duration.ofMinutes(30));

        URI uri = URI.create(readUrl);
        assertThat(uri.getHost()).isEqualTo("moyeorock-test-files.s3.ap-northeast-2.amazonaws.com");
        assertThat(uri.getPath()).isEqualTo("/practice/abc.m4a");
        assertThat(uri.getQuery()).contains("X-Amz-Expires=1800").contains("X-Amz-Signature=");
    }

    @Test
    @DisplayName("올라간 객체는 HeadObject의 크기를 돌려준다")
    void size_returnsContentLength() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(52_428_800L).build());

        assertThat(client.size("practice/abc.m4a")).hasValue(52_428_800L);
    }

    @Test
    @DisplayName("객체가 없으면(404) 빈 값이다")
    void size_returnsEmpty_whenNotFound() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().statusCode(404).message("Not Found").build());

        assertThat(client.size("practice/none.m4a")).isEmpty();
    }

    @Test
    @DisplayName("ListBucket 권한이 없으면 없는 객체도 403이라 빈 값으로 본다")
    void size_returnsEmpty_whenForbidden() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(403).message("Forbidden").build());

        assertThat(client.size("practice/none.m4a")).isEmpty();
    }

    @Test
    @DisplayName("그 밖의 S3 오류는 그대로 던진다")
    void size_rethrows_onOtherErrors() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(500).message("Internal Error").build());

        assertThatThrownBy(() -> client.size("practice/abc.m4a")).isInstanceOf(S3Exception.class);
    }

    @Test
    @DisplayName("파일을 읽으면 GetObject로 이 버킷의 객체 내용을 돌려준다(연습 녹음 분석, 이슈 #167)")
    void read_returnsObjectContent() throws IOException {
        byte[] audio = {1, 2, 3, 4};
        when(s3Client.getObject(any(GetObjectRequest.class))).thenAnswer(invocation -> {
            GetObjectRequest request = invocation.getArgument(0);
            assertThat(request.bucket()).isEqualTo(BUCKET);
            assertThat(request.key()).isEqualTo("practice/abc.m4a");
            return new ResponseInputStream<>(GetObjectResponse.builder().contentLength(4L).build(),
                    AbortableInputStream.create(new ByteArrayInputStream(audio)));
        });

        try (InputStream content = client.read("practice/abc.m4a")) {
            assertThat(content.readAllBytes()).isEqualTo(audio);
        }
    }

    @Test
    @DisplayName("읽을 객체가 없으면(404) 로컬과 같게 NoSuchFileException으로 알린다")
    void read_throwsNoSuchFile_whenNotFound() {
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().statusCode(404).message("Not Found").build());

        assertThatThrownBy(() -> client.read("practice/none.m4a"))
                .isInstanceOf(UncheckedIOException.class)
                .hasCauseInstanceOf(NoSuchFileException.class);
    }

    @Test
    @DisplayName("ListBucket 권한이 없으면 없는 객체도 403이라 없는 파일로 본다")
    void read_throwsNoSuchFile_whenForbidden() {
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(403).message("Forbidden").build());

        assertThatThrownBy(() -> client.read("practice/none.m4a"))
                .isInstanceOf(UncheckedIOException.class)
                .hasCauseInstanceOf(NoSuchFileException.class);
    }

    @Test
    @DisplayName("그 밖의 S3 오류는 읽기에서도 그대로 던진다")
    void read_rethrows_onOtherErrors() {
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(500).message("Internal Error").build());

        assertThatThrownBy(() -> client.read("practice/abc.m4a")).isInstanceOf(S3Exception.class);
    }
}

package com.moyeorock.global.file.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * fileUrl → fileKey 변환(이슈 #164 — 파일 삭제가 fileUrl로 받는다). 문자열 규칙만 보므로 S3를 부르는 S3Client·S3Presigner는
 * 넘기지 않는다. issuePresignedUrl의 fileUrl과 같은 접두사(fileUrlPrefix)를 쓰는지가 요점이다.
 */
class S3FileStorageClientTest {

    private static final String OBJECT_URL_PREFIX = "https://moyeorock-test-files.s3.ap-northeast-2.amazonaws.com/";

    private final S3FileStorageClient client = new S3FileStorageClient(
            new S3FileStorageProperties("moyeorock-test-files", "ap-northeast-2", 600), null, null);

    @Test
    @DisplayName("이 버킷의 객체 주소(fileUrl)를 fileKey로 되돌린다")
    void toFileKey_returnsKey_forObjectUrl() {
        assertThat(client.toFileKey(OBJECT_URL_PREFIX + "profile/abc.png")).contains("profile/abc.png");
    }

    @Test
    @DisplayName("다른 버킷·다른 리전·S3가 아닌 URL은 fileKey로 바꾸지 않는다")
    void toFileKey_returnsEmpty_forForeignUrl() {
        assertThat(client.toFileKey("https://other-bucket.s3.ap-northeast-2.amazonaws.com/profile/abc.png")).isEmpty();
        assertThat(client.toFileKey("https://moyeorock-test-files.s3.us-east-1.amazonaws.com/profile/abc.png"))
                .isEmpty();
        assertThat(client.toFileKey("http://localhost:8080/v1/files/local/profile/abc.png")).isEmpty();
        assertThat(client.toFileKey(OBJECT_URL_PREFIX)).isEmpty();
        assertThat(client.toFileKey(null)).isEmpty();
    }
}

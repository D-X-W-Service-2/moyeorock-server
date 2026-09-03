package com.moyeorock.global.file.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class S3PresignedUrlGeneratorTest {

    // 서명은 로컬 연산이라 더미 자격증명으로도 URL이 생성된다 — 네트워크 불필요
    private final S3Presigner presigner = S3Presigner.builder()
            .region(Region.AP_NORTHEAST_2)
            .credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create("test-access-key", "test-secret-key")))
            .build();

    @AfterEach
    void tearDown() {
        presigner.close();
    }

    private S3PresignedUrlGenerator generator(String bucket, String publicBaseUrl) {
        return new S3PresignedUrlGenerator(presigner, new S3Properties(bucket, "ap-northeast-2", publicBaseUrl));
    }

    @Test
    @DisplayName("업로드 URL은 버킷·파일 키·만료 시간이 서명된 S3 PUT URL이다")
    void uploadUrl() {
        PresignedUrlGenerator.Result result = generator("test-bucket", "")
                .issue("performance/abc.png", "image/png", Duration.ofMinutes(10));

        assertThat(result.uploadUrl())
                .contains("test-bucket")
                .contains("performance/abc.png")
                .contains("X-Amz-Expires=600")
                .contains("X-Amz-Signature=");
    }

    @Test
    @DisplayName("publicBaseUrl이 비어 있으면 fileUrl은 S3 기본 URL이다")
    void fileUrlWithoutCdn() {
        PresignedUrlGenerator.Result result = generator("test-bucket", "")
                .issue("performance/abc.png", "image/png", Duration.ofMinutes(10));

        assertThat(result.fileUrl())
                .isEqualTo("https://test-bucket.s3.ap-northeast-2.amazonaws.com/performance/abc.png");
    }

    @Test
    @DisplayName("publicBaseUrl이 설정되면 fileUrl은 CDN 도메인 기준이다 (끝 슬래시 무관)")
    void fileUrlWithCdn() {
        PresignedUrlGenerator.Result result = generator("test-bucket", "https://cdn.example.com/")
                .issue("profile/abc.jpg", "image/jpeg", Duration.ofMinutes(10));

        assertThat(result.fileUrl()).isEqualTo("https://cdn.example.com/profile/abc.jpg");
    }

    @Test
    @DisplayName("버킷 미설정 상태로 발급을 요청하면 설정 안내와 함께 실패한다")
    void bucketNotConfigured() {
        assertThatThrownBy(() -> generator("", "")
                .issue("performance/abc.png", "image/png", Duration.ofMinutes(10)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("S3_BUCKET");
    }

    @Test
    @DisplayName("리전 미설정이면 부팅은 되고 발급 요청 시 설정 안내와 함께 실패한다")
    void regionNotConfigured() {
        S3PresignedUrlGenerator generator =
                new S3PresignedUrlGenerator(new S3Properties("test-bucket", null, ""));

        assertThatThrownBy(() -> generator.issue("performance/abc.png", "image/png", Duration.ofMinutes(10)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AWS_REGION");
    }
}

package com.moyeorock.global.file.client;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

// S3Client/S3Presigner 빈은 file.storage.type=s3일 때만 만들어진다 — 그 전에는 AWS
// 자격증명이 없어도 부팅된다. 자격증명은 SDK 기본 체인(환경변수 AWS_ACCESS_KEY_ID 등)을 쓴다.
@Configuration
@ConditionalOnProperty(prefix = "file.storage", name = "type", havingValue = "s3")
@EnableConfigurationProperties(S3FileStorageProperties.class)
public class S3StorageConfig {

    @Bean
    public S3Client s3Client(S3FileStorageProperties properties) {
        return S3Client.builder()
                .region(Region.of(properties.region()))
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(S3FileStorageProperties properties) {
        return S3Presigner.builder()
                .region(Region.of(properties.region()))
                .build();
    }
}

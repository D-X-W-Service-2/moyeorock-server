package com.moyeorock.global.file.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "file.storage.local")
public record LocalFileStorageProperties(String basePath, String baseUrl, long uploadUrlExpirySeconds) {
}

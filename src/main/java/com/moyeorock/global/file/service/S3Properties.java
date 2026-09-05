package com.moyeorock.global.file.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * bucket이 비어 있어도 컨텍스트는 뜬다 — 버킷 이름·정책이 팀 결정 대기(이슈 #18)라
 * 설정 없는 로컬 환경의 bootRun을 막지 않기 위함. 발급 시점에 검증한다.
 * publicBaseUrl은 CDN 도입 결정(이슈 #18) 전까지 선택값이며, 비면 S3 기본 URL을 쓴다.
 */
@ConfigurationProperties(prefix = "file.s3")
public record S3Properties(String bucket, String region, String publicBaseUrl) {
}

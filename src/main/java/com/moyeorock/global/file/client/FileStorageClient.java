package com.moyeorock.global.file.client;

import java.io.InputStream;
import java.time.Duration;

// local/s3 구현체 둘 다 만들어서 file.storage.type으로 전환한다(기본값 local) — 구현체가
// 실제로 2개라 CLAUDE.md 절대 규칙 3(인터페이스+구현체 1:1 금지)에 걸리지 않는다.
// docs/plans/file-upload-presigned-url.md 참고.
public interface FileStorageClient {

    UploadUrl issuePresignedUrl(String fileKey, String contentType);

    // 발급한 uploadUrl의 유효 시간. S3는 서명이 만료를 강제하지만 로컬 업로드 수신은 서버가 직접
    // 확인해야 해서(FileService.receiveUpload) 밖으로 꺼낸다.
    Duration uploadUrlExpiry();

    void write(String fileKey, InputStream content);

    InputStream read(String fileKey);

    void delete(String fileKey);
}

package com.moyeorock.global.file.controller;

import com.moyeorock.global.common.dto.ApiResponse;
import com.moyeorock.global.common.dto.DeleteResponse;
import com.moyeorock.global.file.client.LocalFileStorageClient;
import com.moyeorock.global.file.dto.request.PresignedUrlCreateRequest;
import com.moyeorock.global.file.dto.response.PresignedUrlResponse;
import com.moyeorock.global.file.service.FileService;
import com.moyeorock.global.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/files")
@RequiredArgsConstructor
public class FileController {

    // LocalFileStorageClient가 presigned URL을 발급할 때 쓰는 경로와 같은 값이어야 한다 — 상수를
    // 여기 따로 두면 한쪽만 바뀌었을 때 조용히 깨지므로 그 클래스의 상수를 그대로 참조한다.
    private static final String LOCAL_UPLOAD_PATH = LocalFileStorageClient.LOCAL_UPLOAD_PATH;
    private static final String LOCAL_SERVE_PATH = LocalFileStorageClient.LOCAL_SERVE_PATH;

    private final FileService fileService;

    @PostMapping("/presigned-url")
    public ApiResponse<PresignedUrlResponse> issuePresignedUrl(@AuthUser Long userId,
            @Valid @RequestBody PresignedUrlCreateRequest request) {
        PresignedUrlResponse response = fileService.issuePresignedUrl(
                request.domain(), request.fileName(), request.contentType(), userId);
        return ApiResponse.success(response);
    }

    @DeleteMapping("/{fileId}")
    public ApiResponse<DeleteResponse> delete(@AuthUser Long userId, @PathVariable Long fileId) {
        fileService.delete(fileId, userId);
        return ApiResponse.success(DeleteResponse.of(fileId));
    }

    /**
     * S3 전환 전 임시 엔드포인트. 로컬 스토리지의 issuePresignedUrl이 내려준 uploadUrl이 이 경로를
     * 가리킨다. S3로 전환하면 이 메서드와 LocalFileStorageClient를 함께 제거한다.
     */
    @PutMapping("/local-upload/**")
    public ApiResponse<Void> receiveLocalUpload(@AuthUser Long userId, HttpServletRequest request)
            throws IOException {
        String fileKey = extractFileKey(request, LOCAL_UPLOAD_PATH);
        fileService.receiveUpload(fileKey, request.getInputStream(), userId);
        return ApiResponse.success(null);
    }

    /**
     * S3 전환 전 임시 엔드포인트. CDN을 안 쓰기로 해서(docs/plans/file-upload-presigned-url.md)
     * 로컬 저장 시엔 이 서버가 직접 파일을 내려준다. 프로필·커버·포스터는 공개 정보라 인증 없이 연다
     * (PublicEndpoints 등록). 원시 바이트를 그대로 내려줘야 해서 api-conventions.md §1의
     * "모든 응답은 ApiResponse로 감싼다" 규칙의 예외다 — Location 헤더 응답과 같은 성격의 예외.
     */
    @GetMapping("/local/**")
    public ResponseEntity<InputStreamResource> serveLocal(HttpServletRequest request) {
        String fileKey = extractFileKey(request, LOCAL_SERVE_PATH);
        FileService.FileContent content = fileService.readLocalFile(fileKey);
        MediaType mediaType = parseMediaTypeOrClose(content);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .body(new InputStreamResource(content.content()));
    }

    // contentType은 issuePresignedUrl 시점에 화이트리스트로 검증되지만, 그 이전에 발급된 값이나
    // 향후 검증 로직 변경에 대비해 파싱 실패 시 이미 열려 있는 스트림을 여기서 반드시 닫는다 —
    // 안 닫으면 인증 없는 이 엔드포인트를 반복 호출해 파일 디스크립터를 고갈시킬 수 있다.
    private MediaType parseMediaTypeOrClose(FileService.FileContent content) {
        try {
            return MediaType.parseMediaType(content.contentType());
        } catch (InvalidMediaTypeException e) {
            closeQuietly(content.content());
            throw e;
        }
    }

    private void closeQuietly(InputStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
            // 닫기 실패는 무시 — 이미 예외 처리 경로라 원래 예외를 그대로 전파한다.
        }
    }

    private String extractFileKey(HttpServletRequest request, String prefix) {
        String uri = request.getRequestURI();
        int index = uri.indexOf(prefix);
        return uri.substring(index + prefix.length());
    }
}

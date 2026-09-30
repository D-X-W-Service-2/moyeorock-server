package com.moyeorock.global.file.controller;

import com.moyeorock.global.common.dto.ApiResponse;
import com.moyeorock.global.common.dto.DeleteResponse;
import com.moyeorock.global.file.dto.request.PresignedUrlCreateRequest;
import com.moyeorock.global.file.dto.response.PresignedUrlResponse;
import com.moyeorock.global.file.service.FileService;
import com.moyeorock.global.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
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

    private static final String LOCAL_UPLOAD_PATH = "/v1/files/local-upload/";
    private static final String LOCAL_SERVE_PATH = "/v1/files/local/";

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
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .body(new InputStreamResource(content.content()));
    }

    private String extractFileKey(HttpServletRequest request, String prefix) {
        String uri = request.getRequestURI();
        int index = uri.indexOf(prefix);
        return uri.substring(index + prefix.length());
    }
}

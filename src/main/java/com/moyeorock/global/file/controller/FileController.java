package com.moyeorock.global.file.controller;

import com.moyeorock.global.common.dto.ApiResponse;
import com.moyeorock.global.file.dto.request.PresignedUrlCreateRequest;
import com.moyeorock.global.file.dto.response.PresignedUrlResponse;
import com.moyeorock.global.file.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v0/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @Operation(summary = "업로드 presigned URL 발급")
    @PostMapping("/presigned-url")
    public ApiResponse<PresignedUrlResponse> createPresignedUrl(@Valid @RequestBody PresignedUrlCreateRequest request) {
        return ApiResponse.success(fileService.issuePresignedUrl(request));
    }
}

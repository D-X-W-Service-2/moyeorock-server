package com.moyeorock.global.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import com.moyeorock.global.file.client.FileStorageClient;
import com.moyeorock.global.file.client.UploadUrl;
import com.moyeorock.global.file.dto.response.PresignedUrlResponse;
import com.moyeorock.global.file.entity.UploadedFile;
import com.moyeorock.global.file.enums.UploadDomain;
import com.moyeorock.global.file.repository.UploadedFileRepository;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    private static final Long UPLOADER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    @Mock
    private UploadedFileRepository uploadedFileRepository;

    @Mock
    private FileStorageClient fileStorageClient;

    private FileService fileService;

    @BeforeEach
    void setUp() {
        fileService = new FileService(uploadedFileRepository, fileStorageClient);
    }

    @Test
    @DisplayName("허용된 확장자면 presigned URL을 발급하고 files 행을 저장한다")
    void issuePresignedUrl_succeeds_forAllowedExtension() {
        UploadUrl uploadUrl = new UploadUrl(
                "https://example.com/upload", "https://example.com/file", LocalDateTime.now().plusMinutes(10));
        when(fileStorageClient.issuePresignedUrl(any(), any())).thenReturn(uploadUrl);
        when(uploadedFileRepository.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0), 10L));

        PresignedUrlResponse response = fileService.issuePresignedUrl(
                UploadDomain.PROFILE_IMAGE, "me.png", "image/png", UPLOADER_ID);

        assertThat(response.fileId()).isEqualTo(10L);
        assertThat(response.uploadUrl()).isEqualTo("https://example.com/upload");
        assertThat(response.fileUrl()).isEqualTo("https://example.com/file");
    }

    @Test
    @DisplayName("허용 안 된 확장자면 UNSUPPORTED_FILE_TYPE 예외를 던지고 스토리지를 건드리지 않는다")
    void issuePresignedUrl_throws_forDisallowedExtension() {
        assertThatThrownBy(() -> fileService.issuePresignedUrl(
                UploadDomain.PROFILE_IMAGE, "malicious.svg", "image/svg+xml", UPLOADER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNSUPPORTED_FILE_TYPE);
        verifyNoInteractions(fileStorageClient);
    }

    @Test
    @DisplayName("확장자가 없는 파일명이면 UNSUPPORTED_FILE_TYPE 예외를 던진다")
    void issuePresignedUrl_throws_whenNoExtension() {
        assertThatThrownBy(() -> fileService.issuePresignedUrl(
                UploadDomain.PROFILE_IMAGE, "no-extension", "image/png", UPLOADER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNSUPPORTED_FILE_TYPE);
    }

    @Test
    @DisplayName("업로더 본인이면 업로드 수신이 성공한다")
    void receiveUpload_succeeds_forUploader() {
        UploadedFile file = UploadedFile.create("profile/key.png", UploadDomain.PROFILE_IMAGE, "me.png",
                "image/png", UPLOADER_ID);
        when(uploadedFileRepository.findByFileKey("profile/key.png")).thenReturn(Optional.of(file));
        InputStream content = new ByteArrayInputStream(new byte[]{1, 2, 3});

        fileService.receiveUpload("profile/key.png", content, UPLOADER_ID);

        verify(fileStorageClient).write("profile/key.png", content);
    }

    @Test
    @DisplayName("발급된 적 없는 fileKey면 FILE_NOT_FOUND 예외를 던진다")
    void receiveUpload_throws_whenFileKeyUnknown() {
        when(uploadedFileRepository.findByFileKey("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.receiveUpload("unknown", InputStream.nullInputStream(), UPLOADER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FILE_NOT_FOUND);
    }

    @Test
    @DisplayName("업로더 본인이 아니면 NO_PERMISSION 예외를 던지고 스토리지에 쓰지 않는다")
    void receiveUpload_throws_whenNotUploader() {
        UploadedFile file = UploadedFile.create("profile/key.png", UploadDomain.PROFILE_IMAGE, "me.png",
                "image/png", UPLOADER_ID);
        when(uploadedFileRepository.findByFileKey("profile/key.png")).thenReturn(Optional.of(file));

        assertThatThrownBy(() -> fileService.receiveUpload("profile/key.png", InputStream.nullInputStream(),
                OTHER_USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NO_PERMISSION);
        verifyNoInteractions(fileStorageClient);
    }

    @Test
    @DisplayName("업로더 본인이면 삭제가 성공하고 스토리지·DB 양쪽에서 지운다")
    void delete_succeeds_forUploader() {
        UploadedFile file = withId(UploadedFile.create("profile/key.png", UploadDomain.PROFILE_IMAGE, "me.png",
                "image/png", UPLOADER_ID), 5L);
        when(uploadedFileRepository.findById(5L)).thenReturn(Optional.of(file));

        fileService.delete(5L, UPLOADER_ID);

        verify(fileStorageClient).delete("profile/key.png");
        verify(uploadedFileRepository).delete(file);
    }

    @Test
    @DisplayName("존재하지 않는 fileId를 삭제하려 하면 FILE_NOT_FOUND 예외를 던진다")
    void delete_throws_whenFileNotFound() {
        when(uploadedFileRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.delete(99L, UPLOADER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FILE_NOT_FOUND);
    }

    @Test
    @DisplayName("업로더 본인이 아니면 삭제 시 NO_PERMISSION 예외를 던진다")
    void delete_throws_whenNotUploader() {
        UploadedFile file = withId(UploadedFile.create("profile/key.png", UploadDomain.PROFILE_IMAGE, "me.png",
                "image/png", UPLOADER_ID), 5L);
        when(uploadedFileRepository.findById(5L)).thenReturn(Optional.of(file));

        assertThatThrownBy(() -> fileService.delete(5L, OTHER_USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NO_PERMISSION);
        verifyNoInteractions(fileStorageClient);
    }

    // UploadedFile.id는 GeneratedValue라 create()로는 못 채운다 — 리플렉션으로 저장 후 채워지는 것처럼 흉내낸다.
    private static UploadedFile withId(UploadedFile file, Long id) {
        try {
            Field idField = UploadedFile.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(file, id);
            return file;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}

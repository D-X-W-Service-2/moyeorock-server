package com.moyeorock.global.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
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
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.file.NoSuchFileException;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
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
    @DisplayName("확장자는 허용돼도 contentType이 화이트리스트에 없으면 UNSUPPORTED_FILE_TYPE 예외를 던진다")
    void issuePresignedUrl_throws_whenContentTypeNotAllowed() {
        // fileName만 보고 확장자를 검증하면 contentType=text/html로 발급받아 로컬 서빙 엔드포인트가
        // 그대로 echo하는 저장형 XSS가 가능해진다 — contentType도 반드시 별도로 막아야 한다.
        assertThatThrownBy(() -> fileService.issuePresignedUrl(
                UploadDomain.PROFILE_IMAGE, "me.png", "text/html", UPLOADER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNSUPPORTED_FILE_TYPE);
        verifyNoInteractions(fileStorageClient);
    }

    @Test
    @DisplayName("업로더 본인이면 업로드 수신이 성공한다")
    void receiveUpload_succeeds_forUploader() throws Exception {
        UploadedFile file = UploadedFile.create("profile/key.png", UploadDomain.PROFILE_IMAGE, "me.png",
                "image/png", UPLOADER_ID);
        when(uploadedFileRepository.findByFileKey("profile/key.png")).thenReturn(Optional.of(file));
        byte[] bytes = {1, 2, 3};
        InputStream content = new ByteArrayInputStream(bytes);

        fileService.receiveUpload("profile/key.png", content, UPLOADER_ID);

        // 용량 제한을 걸기 위해 원본 스트림을 SizeLimitedInputStream으로 감싸서 넘기므로 참조 동등성이
        // 아니라 실제로 읽히는 바이트가 같은지로 검증한다.
        var captor = org.mockito.ArgumentCaptor.forClass(InputStream.class);
        verify(fileStorageClient).write(org.mockito.ArgumentMatchers.eq("profile/key.png"), captor.capture());
        assertThat(captor.getValue().readAllBytes()).isEqualTo(bytes);
    }

    @Test
    @DisplayName("업로드 용량 제한을 넘기면 FILE_SIZE_EXCEEDED 예외를 던진다")
    void receiveUpload_throws_whenSizeExceeded() {
        UploadedFile file = UploadedFile.create("profile/key.png", UploadDomain.PROFILE_IMAGE, "me.png",
                "image/png", UPLOADER_ID);
        when(uploadedFileRepository.findByFileKey("profile/key.png")).thenReturn(Optional.of(file));
        // write() 호출 시 실제로 스트림을 끝까지 읽어야 SizeLimitedInputStream이 한도 초과를 감지한다 —
        // LocalFileStorageClient가 하듯 여기서도 끝까지 소비해서 검증한다.
        org.mockito.Mockito.doAnswer(invocation -> {
            InputStream stream = invocation.getArgument(1);
            try {
                stream.readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return null;
        }).when(fileStorageClient).write(anyString(), any());
        InputStream oversized = new ByteArrayInputStream(new byte[11 * 1024 * 1024]);

        assertThatThrownBy(() -> fileService.receiveUpload("profile/key.png", oversized, UPLOADER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FILE_SIZE_EXCEEDED);
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
    @DisplayName("삭제 시 DB에서 먼저 지우고 스토리지(비가역)를 나중에 지운다")
    void delete_deletesDbBeforeStorage() {
        // 스토리지 삭제를 먼저 하면, 그 다음 DB 삭제 커밋이 실패했을 때 "파일은 없는데 DB row만 남는"
        // 상태가 될 수 있다 — 순서를 반대로 둬서 그 창을 줄인다.
        UploadedFile file = withId(UploadedFile.create("profile/key.png", UploadDomain.PROFILE_IMAGE, "me.png",
                "image/png", UPLOADER_ID), 5L);
        when(uploadedFileRepository.findById(5L)).thenReturn(Optional.of(file));

        fileService.delete(5L, UPLOADER_ID);

        InOrder order = inOrder(uploadedFileRepository, fileStorageClient);
        order.verify(uploadedFileRepository).delete(file);
        order.verify(fileStorageClient).delete("profile/key.png");
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

    @Test
    @DisplayName("DB엔 row가 있지만 실제 파일이 없으면(업로드 전/중) 500이 아니라 FILE_NOT_FOUND 예외를 던진다")
    void readLocalFile_throws_FILE_NOT_FOUND_whenPhysicalFileMissing() {
        UploadedFile file = UploadedFile.create("profile/key.png", UploadDomain.PROFILE_IMAGE, "me.png",
                "image/png", UPLOADER_ID);
        when(uploadedFileRepository.findByFileKey("profile/key.png")).thenReturn(Optional.of(file));
        when(fileStorageClient.read("profile/key.png"))
                .thenThrow(new UncheckedIOException(new NoSuchFileException("profile/key.png")));

        assertThatThrownBy(() -> fileService.readLocalFile("profile/key.png"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FILE_NOT_FOUND);
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

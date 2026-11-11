package com.moyeorock.global.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.moyeorock.global.common.entity.BaseTimeEntity;
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
import java.time.Duration;
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
    private static final Duration EXPIRY = Duration.ofMinutes(10);

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
        UploadedFile file = issuedAt(UploadedFile.create("profile/key.png", UploadDomain.PROFILE_IMAGE, "me.png",
                "image/png", UPLOADER_ID), LocalDateTime.now());
        when(uploadedFileRepository.findByFileKey("profile/key.png")).thenReturn(Optional.of(file));
        when(fileStorageClient.uploadUrlExpiry()).thenReturn(EXPIRY);
        byte[] bytes = {1, 2, 3};
        InputStream content = new ByteArrayInputStream(bytes);

        fileService.receiveUpload("profile/key.png", "image/png", content, UPLOADER_ID);

        // 용량 제한을 걸기 위해 원본 스트림을 SizeLimitedInputStream으로 감싸서 넘기므로 참조 동등성이
        // 아니라 실제로 읽히는 바이트가 같은지로 검증한다.
        var captor = org.mockito.ArgumentCaptor.forClass(InputStream.class);
        verify(fileStorageClient).write(org.mockito.ArgumentMatchers.eq("profile/key.png"), captor.capture());
        assertThat(captor.getValue().readAllBytes()).isEqualTo(bytes);
    }

    @Test
    @DisplayName("업로드 용량 제한을 넘기면 FILE_SIZE_EXCEEDED 예외를 던진다")
    void receiveUpload_throws_whenSizeExceeded() {
        UploadedFile file = issuedAt(UploadedFile.create("profile/key.png", UploadDomain.PROFILE_IMAGE, "me.png",
                "image/png", UPLOADER_ID), LocalDateTime.now());
        when(uploadedFileRepository.findByFileKey("profile/key.png")).thenReturn(Optional.of(file));
        when(fileStorageClient.uploadUrlExpiry()).thenReturn(EXPIRY);
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

        assertThatThrownBy(() -> fileService.receiveUpload("profile/key.png", "image/png", oversized, UPLOADER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FILE_SIZE_EXCEEDED);
    }

    @Test
    @DisplayName("발급된 적 없는 fileKey면 FILE_NOT_FOUND 예외를 던진다")
    void receiveUpload_throws_whenFileKeyUnknown() {
        when(uploadedFileRepository.findByFileKey("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.receiveUpload("unknown", "image/png", InputStream.nullInputStream(),
                UPLOADER_ID))
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

        assertThatThrownBy(() -> fileService.receiveUpload("profile/key.png", "image/png",
                InputStream.nullInputStream(), OTHER_USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NO_PERMISSION);
        verifyNoInteractions(fileStorageClient);
    }

    @Test
    @DisplayName("발급 후 유효 시간이 지나면 UPLOAD_URL_EXPIRED 예외를 던지고 스토리지에 쓰지 않는다")
    void receiveUpload_throws_whenUploadUrlExpired() {
        UploadedFile file = issuedAt(UploadedFile.create("profile/key.png", UploadDomain.PROFILE_IMAGE, "me.png",
                "image/png", UPLOADER_ID), LocalDateTime.now().minus(EXPIRY).minusMinutes(1));
        when(uploadedFileRepository.findByFileKey("profile/key.png")).thenReturn(Optional.of(file));
        when(fileStorageClient.uploadUrlExpiry()).thenReturn(EXPIRY);

        assertThatThrownBy(() -> fileService.receiveUpload("profile/key.png", "image/png",
                new ByteArrayInputStream(new byte[]{1}), UPLOADER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UPLOAD_URL_EXPIRED);
        verify(fileStorageClient, never()).write(anyString(), any());
    }

    @Test
    @DisplayName("유효 시간 안이면 이미 올린 키에 다시 올려도 받는다(S3 presigned URL과 같은 동작)")
    void receiveUpload_acceptsReupload_beforeExpiry() {
        UploadedFile file = issuedAt(UploadedFile.create("profile/key.png", UploadDomain.PROFILE_IMAGE, "me.png",
                "image/png", UPLOADER_ID), LocalDateTime.now().minus(EXPIRY).plusMinutes(1));
        when(uploadedFileRepository.findByFileKey("profile/key.png")).thenReturn(Optional.of(file));
        when(fileStorageClient.uploadUrlExpiry()).thenReturn(EXPIRY);

        fileService.receiveUpload("profile/key.png", "image/png", new ByteArrayInputStream(new byte[]{1}), UPLOADER_ID);
        fileService.receiveUpload("profile/key.png", "image/png", new ByteArrayInputStream(new byte[]{2}), UPLOADER_ID);

        verify(fileStorageClient, times(2)).write(org.mockito.ArgumentMatchers.eq("profile/key.png"), any());
    }

    @Test
    @DisplayName("업로더가 아니면 만료 여부와 상관없이 NO_PERMISSION을 먼저 던진다")
    void receiveUpload_checksUploaderBeforeExpiry() {
        // 만료 검사를 먼저 하면 남의 파일에 대해 발급 시각(만료 여부)이 응답 코드로 드러난다.
        UploadedFile file = issuedAt(UploadedFile.create("profile/key.png", UploadDomain.PROFILE_IMAGE, "me.png",
                "image/png", UPLOADER_ID), LocalDateTime.now().minus(EXPIRY).minusMinutes(1));
        when(uploadedFileRepository.findByFileKey("profile/key.png")).thenReturn(Optional.of(file));

        assertThatThrownBy(() -> fileService.receiveUpload("profile/key.png", "image/png",
                new ByteArrayInputStream(new byte[]{1}), OTHER_USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NO_PERMISSION);
        verifyNoInteractions(fileStorageClient);
    }

    @Test
    @DisplayName("요청 Content-Type이 발급 때 정한 값과 다르면 UNSUPPORTED_MEDIA_TYPE, 스토리지에 쓰지 않는다")
    void receiveUpload_throws_whenContentTypeMismatch() {
        assertReceiveUploadRejectsContentType("image/jpeg");
    }

    @Test
    @DisplayName("multipart/form-data로 보내면 UNSUPPORTED_MEDIA_TYPE, 스토리지에 쓰지 않는다")
    void receiveUpload_throws_whenMultipart() {
        // multipart면 Spring이 바디를 먼저 파싱해 빈 스트림만 넘어온다 — 막지 않으면 빈 파일이 200으로 저장된다.
        assertReceiveUploadRejectsContentType("multipart/form-data; boundary=----x");
    }

    @Test
    @DisplayName("Content-Type 헤더가 없으면 UNSUPPORTED_MEDIA_TYPE, 스토리지에 쓰지 않는다")
    void receiveUpload_throws_whenContentTypeMissing() {
        assertReceiveUploadRejectsContentType(null);
    }

    @Test
    @DisplayName("Content-Type은 대소문자만 다르면 같은 값으로 본다")
    void receiveUpload_acceptsContentType_ignoringCase() {
        UploadedFile file = issuedAt(UploadedFile.create("profile/key.png", UploadDomain.PROFILE_IMAGE, "me.png",
                "image/png", UPLOADER_ID), LocalDateTime.now());
        when(uploadedFileRepository.findByFileKey("profile/key.png")).thenReturn(Optional.of(file));
        when(fileStorageClient.uploadUrlExpiry()).thenReturn(EXPIRY);

        fileService.receiveUpload("profile/key.png", "IMAGE/PNG", new ByteArrayInputStream(new byte[]{1}), UPLOADER_ID);

        verify(fileStorageClient).write(org.mockito.ArgumentMatchers.eq("profile/key.png"), any());
    }

    @Test
    @DisplayName("바디가 비어 있으면 EMPTY_FILE 예외를 던지고 스토리지에 쓰지 않는다")
    void receiveUpload_throws_whenBodyEmpty() {
        UploadedFile file = issuedAt(UploadedFile.create("profile/key.png", UploadDomain.PROFILE_IMAGE, "me.png",
                "image/png", UPLOADER_ID), LocalDateTime.now());
        when(uploadedFileRepository.findByFileKey("profile/key.png")).thenReturn(Optional.of(file));
        when(fileStorageClient.uploadUrlExpiry()).thenReturn(EXPIRY);

        assertThatThrownBy(() -> fileService.receiveUpload("profile/key.png", "image/png",
                InputStream.nullInputStream(), UPLOADER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EMPTY_FILE);
        verify(fileStorageClient, never()).write(anyString(), any());
    }

    @Test
    @DisplayName("연습 녹음은 mp3·m4a·wav를 받고 practice/ 폴더에 저장할 key를 만든다")
    void issuePresignedUrl_practiceAudio_usesPracticeFolder() {
        when(fileStorageClient.issuePresignedUrl(any(), any())).thenReturn(
                new UploadUrl("https://example.com/upload", "https://example.com/file", LocalDateTime.now()));
        when(uploadedFileRepository.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0), 10L));

        fileService.issuePresignedUrl(UploadDomain.PRACTICE_AUDIO, "a.mp3", "audio/mpeg", UPLOADER_ID);
        fileService.issuePresignedUrl(UploadDomain.PRACTICE_AUDIO, "b.M4A", "audio/x-m4a", UPLOADER_ID);
        fileService.issuePresignedUrl(UploadDomain.PRACTICE_AUDIO, "c.wav", "audio/wav", UPLOADER_ID);

        var keyCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(fileStorageClient, times(3)).issuePresignedUrl(keyCaptor.capture(), any());
        assertThat(keyCaptor.getAllValues())
                .allMatch(key -> key.startsWith("practice/"))
                .extracting(key -> key.substring(key.lastIndexOf('.') + 1))
                .containsExactly("mp3", "m4a", "wav");
    }

    @Test
    @DisplayName("도메인과 형식이 안 맞으면(녹음에 이미지, 이미지에 녹음) UNSUPPORTED_FILE_TYPE")
    void issuePresignedUrl_throws_whenTypeDoesNotMatchDomain() {
        assertUnsupportedFileType(UploadDomain.PRACTICE_AUDIO, "cover.png", "image/png");
        assertUnsupportedFileType(UploadDomain.PROFILE_IMAGE, "song.mp3", "audio/mpeg");
        // 확장자는 맞는데 contentType이 다른 도메인 것이면 막는다 — 로컬 서빙이 contentType을 그대로 내보낸다
        assertUnsupportedFileType(UploadDomain.PRACTICE_AUDIO, "song.mp3", "image/png");
        assertUnsupportedFileType(UploadDomain.GROUP_COVER, "cover.png", "audio/mpeg");
        verifyNoInteractions(fileStorageClient);
    }

    @Test
    @DisplayName("연습 녹음은 100MB까지 받는다")
    void receiveUpload_practiceAudio_acceptsUpTo100MB() {
        stubPracticeAudioUpload();

        fileService.receiveUpload("practice/key.m4a", "audio/mp4", zeros(100L * 1024 * 1024), UPLOADER_ID);

        verify(fileStorageClient).write(org.mockito.ArgumentMatchers.eq("practice/key.m4a"), any());
    }

    @Test
    @DisplayName("연습 녹음이 100MB를 넘으면 FILE_SIZE_EXCEEDED")
    void receiveUpload_practiceAudio_throws_whenOver100MB() {
        stubPracticeAudioUpload();

        assertThatThrownBy(() -> fileService.receiveUpload("practice/key.m4a", "audio/mp4",
                zeros(100L * 1024 * 1024 + 1), UPLOADER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FILE_SIZE_EXCEEDED);
    }

    @Test
    @DisplayName("내가 그 도메인으로 올린 파일의 fileUrl이면 스토리지 key를 돌려준다")
    void findOwnedFileKey_returnsKey_forOwnFileInDomain() {
        when(fileStorageClient.toFileKey("https://files/practice/key.m4a")).thenReturn(Optional.of("practice/key.m4a"));
        when(uploadedFileRepository.findByFileKey("practice/key.m4a")).thenReturn(Optional.of(
                UploadedFile.create("practice/key.m4a", UploadDomain.PRACTICE_AUDIO, "a.m4a", "audio/mp4", UPLOADER_ID)));

        assertThat(fileService.findOwnedFileKey("https://files/practice/key.m4a", UploadDomain.PRACTICE_AUDIO,
                UPLOADER_ID)).contains("practice/key.m4a");
    }

    @Test
    @DisplayName("남이 올렸거나 다른 도메인 파일이면 빈 값이다")
    void findOwnedFileKey_returnsEmpty_forOthersFileOrOtherDomain() {
        when(fileStorageClient.toFileKey("https://files/practice/key.m4a")).thenReturn(Optional.of("practice/key.m4a"));
        when(uploadedFileRepository.findByFileKey("practice/key.m4a")).thenReturn(Optional.of(
                UploadedFile.create("practice/key.m4a", UploadDomain.PRACTICE_AUDIO, "a.m4a", "audio/mp4", UPLOADER_ID)));
        when(fileStorageClient.toFileKey("https://files/profile/me.png")).thenReturn(Optional.of("profile/me.png"));
        when(uploadedFileRepository.findByFileKey("profile/me.png")).thenReturn(Optional.of(
                UploadedFile.create("profile/me.png", UploadDomain.PROFILE_IMAGE, "me.png", "image/png", UPLOADER_ID)));

        assertThat(fileService.findOwnedFileKey("https://files/practice/key.m4a", UploadDomain.PRACTICE_AUDIO,
                OTHER_USER_ID)).isEmpty();
        assertThat(fileService.findOwnedFileKey("https://files/profile/me.png", UploadDomain.PRACTICE_AUDIO,
                UPLOADER_ID)).isEmpty();
    }

    @Test
    @DisplayName("이 저장소의 URL이 아니거나 발급 이력이 없으면 빈 값이다")
    void findOwnedFileKey_returnsEmpty_forForeignUrlOrUnknownKey() {
        when(fileStorageClient.toFileKey("https://evil.example.com/a.m4a")).thenReturn(Optional.empty());
        when(fileStorageClient.toFileKey("https://files/practice/none.m4a")).thenReturn(Optional.of("practice/none.m4a"));
        when(uploadedFileRepository.findByFileKey("practice/none.m4a")).thenReturn(Optional.empty());

        assertThat(fileService.findOwnedFileKey("https://evil.example.com/a.m4a", UploadDomain.PRACTICE_AUDIO,
                UPLOADER_ID)).isEmpty();
        assertThat(fileService.findOwnedFileKey("https://files/practice/none.m4a", UploadDomain.PRACTICE_AUDIO,
                UPLOADER_ID)).isEmpty();
        verify(uploadedFileRepository, never()).findByFileKey("https://evil.example.com/a.m4a");
    }

    @Test
    @DisplayName("업로드 크기 한도는 이미지 10MB, 연습 녹음 100MB다")
    void maxUploadBytes_perDomain() {
        assertThat(fileService.maxUploadBytes(UploadDomain.PROFILE_IMAGE)).isEqualTo(10L * 1024 * 1024);
        assertThat(fileService.maxUploadBytes(UploadDomain.GROUP_COVER)).isEqualTo(10L * 1024 * 1024);
        assertThat(fileService.maxUploadBytes(UploadDomain.PERFORMANCE_POSTER)).isEqualTo(10L * 1024 * 1024);
        assertThat(fileService.maxUploadBytes(UploadDomain.PRACTICE_AUDIO)).isEqualTo(100L * 1024 * 1024);
    }

    // 삭제 성공 경로는 스토리지 삭제가 커밋 이후(afterCommit)에 일어나서 트랜잭션 없는 단위 테스트로는
    // 검증할 수 없다 — FileServiceDeleteTest(실제 트랜잭션)에서 커밋·롤백 양쪽을 본다.

    @Test
    @DisplayName("이 저장소가 만든 fileUrl이 아니면 files 행을 찾지 않고 FILE_NOT_FOUND 예외를 던진다")
    void delete_throws_whenUrlNotFromThisStorage() {
        when(fileStorageClient.toFileKey("https://evil.example.com/profile/key.png")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.delete("https://evil.example.com/profile/key.png", UPLOADER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FILE_NOT_FOUND);
        verifyNoInteractions(uploadedFileRepository);
    }

    @Test
    @DisplayName("발급 이력이 없는(이미 지워진) fileUrl이면 FILE_NOT_FOUND 예외를 던진다")
    void delete_throws_whenFileNotFound() {
        when(fileStorageClient.toFileKey("https://files/profile/none.png")).thenReturn(Optional.of("profile/none.png"));
        when(uploadedFileRepository.findByFileKey("profile/none.png")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.delete("https://files/profile/none.png", UPLOADER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FILE_NOT_FOUND);
    }

    @Test
    @DisplayName("업로더 본인이 아니면 삭제 시 NO_PERMISSION 예외를 던지고 행·저장소를 건드리지 않는다")
    void delete_throws_whenNotUploader() {
        UploadedFile file = withId(UploadedFile.create("profile/key.png", UploadDomain.PROFILE_IMAGE, "me.png",
                "image/png", UPLOADER_ID), 5L);
        when(fileStorageClient.toFileKey("https://files/profile/key.png")).thenReturn(Optional.of("profile/key.png"));
        when(uploadedFileRepository.findByFileKey("profile/key.png")).thenReturn(Optional.of(file));

        assertThatThrownBy(() -> fileService.delete("https://files/profile/key.png", OTHER_USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NO_PERMISSION);
        verify(uploadedFileRepository, never()).delete(any());
        verify(fileStorageClient, never()).delete(anyString());
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

    private void assertReceiveUploadRejectsContentType(String requestContentType) {
        UploadedFile file = issuedAt(UploadedFile.create("profile/key.png", UploadDomain.PROFILE_IMAGE, "me.png",
                "image/png", UPLOADER_ID), LocalDateTime.now());
        when(uploadedFileRepository.findByFileKey("profile/key.png")).thenReturn(Optional.of(file));
        when(fileStorageClient.uploadUrlExpiry()).thenReturn(EXPIRY);

        assertThatThrownBy(() -> fileService.receiveUpload("profile/key.png", requestContentType,
                new ByteArrayInputStream(new byte[]{1}), UPLOADER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        verify(fileStorageClient, never()).write(anyString(), any());
    }

    private void assertUnsupportedFileType(UploadDomain domain, String fileName, String contentType) {
        assertThatThrownBy(() -> fileService.issuePresignedUrl(domain, fileName, contentType, UPLOADER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNSUPPORTED_FILE_TYPE);
    }

    // 연습 녹음 업로드 수신 준비. write()는 LocalFileStorageClient처럼 스트림을 끝까지 소비해야 크기 한도가 걸린다 —
    // 100MB를 메모리에 올리지 않도록 작은 버퍼로 읽고 버린다.
    private void stubPracticeAudioUpload() {
        UploadedFile file = issuedAt(UploadedFile.create("practice/key.m4a", UploadDomain.PRACTICE_AUDIO, "a.m4a",
                "audio/mp4", UPLOADER_ID), LocalDateTime.now());
        when(uploadedFileRepository.findByFileKey("practice/key.m4a")).thenReturn(Optional.of(file));
        when(fileStorageClient.uploadUrlExpiry()).thenReturn(EXPIRY);
        org.mockito.Mockito.doAnswer(invocation -> {
            InputStream stream = invocation.getArgument(1);
            byte[] buffer = new byte[64 * 1024];
            try {
                while (stream.read(buffer) != -1) {
                    // 버린다
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return null;
        }).when(fileStorageClient).write(anyString(), any());
    }

    // 지정한 바이트 수만큼 0을 내는 스트림. 큰 파일 업로드를 메모리 할당 없이 흉내 낸다.
    private static InputStream zeros(long size) {
        return new InputStream() {
            private long remaining = size;

            @Override
            public int read() {
                if (remaining <= 0) {
                    return -1;
                }
                remaining--;
                return 0;
            }

            @Override
            public int read(byte[] b, int off, int len) {
                if (remaining <= 0) {
                    return -1;
                }
                int n = (int) Math.min(len, remaining);
                java.util.Arrays.fill(b, off, off + n, (byte) 0);
                remaining -= n;
                return n;
            }
        };
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

    // createdAt(발급 시각)은 JPA Auditing이 저장 시점에 채운다 — 만료 판단 기준이라 리플렉션으로 넣는다.
    private static UploadedFile issuedAt(UploadedFile file, LocalDateTime createdAt) {
        try {
            Field createdAtField = BaseTimeEntity.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(file, createdAt);
            return file;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}

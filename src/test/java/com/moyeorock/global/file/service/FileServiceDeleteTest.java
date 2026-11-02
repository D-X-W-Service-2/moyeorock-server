package com.moyeorock.global.file.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.global.file.client.FileStorageClient;
import com.moyeorock.global.file.client.LocalFileStorageProperties;
import com.moyeorock.global.file.entity.UploadedFile;
import com.moyeorock.global.file.enums.UploadDomain;
import com.moyeorock.global.file.repository.UploadedFileRepository;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스토리지 삭제는 커밋 이후(afterCommit)에 일어나서 실제 트랜잭션이 있어야 검증할 수 있다 —
 * Mockito 기반 FileServiceTest로는 볼 수 없는 커밋·롤백 두 경로를 여기서 본다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
class FileServiceDeleteTest {

    private static final Long UPLOADER_ID = 1L;

    @Autowired
    private FileService fileService;

    @Autowired
    private UploadedFileRepository uploadedFileRepository;

    @Autowired
    private FileStorageClient fileStorageClient;

    @Autowired
    private LocalFileStorageProperties localFileStorageProperties;

    private final String fileKey = "profile/" + UUID.randomUUID() + ".png";

    @AfterEach
    void cleanUp() throws Exception {
        Files.deleteIfExists(storedPath());
    }

    @Test
    @DisplayName("삭제가 커밋되면 files 행과 스토리지 파일이 모두 사라진다")
    void delete_removesRowAndStoredFile_afterCommit() {
        Long fileId = saveUploadedFile();

        fileService.delete(fileId, UPLOADER_ID);

        assertThat(uploadedFileRepository.findById(fileId)).isEmpty();
        assertThat(Files.exists(storedPath())).isFalse();
    }

    @Test
    @Transactional
    @DisplayName("커밋 전에는 스토리지 파일을 지우지 않는다 — 롤백되면 파일이 그대로 남는다")
    void delete_keepsStoredFile_untilCommit() {
        // 테스트 트랜잭션 안에서 delete를 부르면 커밋 없이 롤백된다. 커밋이 실패하는 경우와 같다 —
        // 이때 파일까지 지워져 있으면 "파일은 없는데 행만 남는" 상태가 된다.
        Long fileId = saveUploadedFile();

        fileService.delete(fileId, UPLOADER_ID);

        assertThat(Files.exists(storedPath())).isTrue();
    }

    private Long saveUploadedFile() {
        UploadedFile file = uploadedFileRepository.save(
                UploadedFile.create(fileKey, UploadDomain.PROFILE_IMAGE, "me.png", "image/png", UPLOADER_ID));
        fileStorageClient.write(fileKey, new ByteArrayInputStream(new byte[]{1, 2, 3}));
        return file.getId();
    }

    private Path storedPath() {
        return Path.of(localFileStorageProperties.basePath()).resolve(fileKey);
    }
}

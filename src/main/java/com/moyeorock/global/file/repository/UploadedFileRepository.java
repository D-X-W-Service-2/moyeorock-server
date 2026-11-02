package com.moyeorock.global.file.repository;

import com.moyeorock.global.file.entity.UploadedFile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadedFileRepository extends JpaRepository<UploadedFile, Long> {

    Optional<UploadedFile> findByFileKey(String fileKey);
}

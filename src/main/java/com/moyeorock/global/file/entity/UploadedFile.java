package com.moyeorock.global.file.entity;

import com.moyeorock.global.common.entity.BaseTimeEntity;
import com.moyeorock.global.file.enums.UploadDomain;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 클래스명을 File로 하면 java.io.File과 겹쳐서 UploadedFile로 뒀다. erd.md #17 "files" 테이블.
// updated_at이 없어(생성 후 안 바뀌는 이력 데이터) BaseTimeEntity만 상속한다(architecture.md §5).
@Entity
@Table(name = "files")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UploadedFile extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_key", nullable = false, length = 255)
    private String fileKey;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private UploadDomain domain;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "uploader_id", nullable = false)
    private Long uploaderId;

    public static UploadedFile create(String fileKey, UploadDomain domain, String originalName,
            String contentType, Long uploaderId) {
        UploadedFile file = new UploadedFile();
        file.fileKey = fileKey;
        file.domain = domain;
        file.originalName = originalName;
        file.contentType = contentType;
        file.uploaderId = uploaderId;
        return file;
    }

    public boolean isUploadedBy(Long userId) {
        return this.uploaderId.equals(userId);
    }
}

package com.moyeorock.global.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import lombok.Getter;
import org.springframework.data.annotation.LastModifiedDate;

/**
 * createdAt + updatedAt을 갖는 기본 엔티티.
 * erd.md 기준 updated_at 컬럼이 있는 테이블에서 상속한다.
 */
@Getter
@MappedSuperclass
public abstract class BaseEntity extends BaseTimeEntity {

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}

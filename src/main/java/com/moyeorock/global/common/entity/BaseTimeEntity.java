package com.moyeorock.global.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * createdAt만 갖는 기본 엔티티.
 * 생성 후 수정 개념이 없는 테이블(erd.md 기준 updated_at 컬럼이 없는 테이블)에서 상속한다.
 * created_at·updated_at이 모두 없는 테이블은 상속 없이 구현한다.
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity {

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

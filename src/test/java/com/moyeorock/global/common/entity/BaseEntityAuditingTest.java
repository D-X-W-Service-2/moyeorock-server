package com.moyeorock.global.common.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.moyeorock.global.config.JpaAuditingConfig;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class BaseEntityAuditingTest {

    @Autowired
    TestEntityManager entityManager;

    @Test
    void 저장하면_createdAt과_updatedAt이_자동으로_채워진다() {
        AuditingProbe saved = entityManager.persistFlushFind(new AuditingProbe());

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    // 테스트 전용 엔티티 — 로컬 DB에 auditing_probe 테이블이 생성된다 (ddl-auto: update)
    @Entity
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    static class AuditingProbe extends BaseEntity {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
    }
}

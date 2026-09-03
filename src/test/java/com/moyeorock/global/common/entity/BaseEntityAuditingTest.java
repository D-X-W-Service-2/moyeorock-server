package com.moyeorock.global.common.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.moyeorock.global.config.JpaAuditingConfig;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

// @DataJpaTest는 @Configuration을 자동 로드하지 않으므로 auditing 설정을 명시적으로 켠다
@DataJpaTest
@Import(JpaAuditingConfig.class)
class BaseEntityAuditingTest {

    @Autowired
    TestEntityManager entityManager;

    @Test
    @DisplayName("저장하면 createdAt과 updatedAt이 자동으로 채워진다")
    void created_at_and_updated_at_are_set_on_persist() {
        AuditingProbe probe = new AuditingProbe();
        Long id = entityManager.persistAndFlush(probe).getId();
        entityManager.clear();

        AuditingProbe found = entityManager.find(AuditingProbe.class, id);

        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("수정하면 updatedAt만 갱신되고 createdAt은 바뀌지 않는다")
    void update_refreshes_updated_at_but_keeps_created_at() throws InterruptedException {
        AuditingProbe probe = new AuditingProbe();
        Long id = entityManager.persistAndFlush(probe).getId();
        LocalDateTime createdAt = probe.getCreatedAt();
        LocalDateTime updatedAtBefore = probe.getUpdatedAt();

        Thread.sleep(10);
        probe.rename("changed");
        entityManager.flush();
        entityManager.clear();

        AuditingProbe found = entityManager.find(AuditingProbe.class, id);

        assertThat(found.getCreatedAt()).isEqualTo(createdAt);
        assertThat(found.getUpdatedAt()).isAfter(updatedAtBefore);
    }

    @Test
    @DisplayName("BaseTimeEntity만 상속해도 createdAt이 자동으로 채워진다")
    void created_at_is_set_on_persist_for_base_time_entity() {
        TimeOnlyProbe probe = new TimeOnlyProbe();
        Long id = entityManager.persistAndFlush(probe).getId();
        entityManager.clear();

        TimeOnlyProbe found = entityManager.find(TimeOnlyProbe.class, id);

        assertThat(found.getCreatedAt()).isNotNull();
    }

    // 테스트 전용 엔티티 — 테스트 설정(src/test/resources)이 인메모리 H2 + create-drop이라 흔적이 남지 않는다
    @Entity
    static class AuditingProbe extends BaseEntity {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        private String name;

        Long getId() {
            return id;
        }

        void rename(String name) {
            this.name = name;
        }
    }

    @Entity
    static class TimeOnlyProbe extends BaseTimeEntity {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        Long getId() {
            return id;
        }
    }
}

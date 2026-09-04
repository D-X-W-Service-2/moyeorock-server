package com.moyeorock.global.common.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.moyeorock.config.TestcontainersConfig;
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
import org.springframework.test.context.TestPropertySource;

// @DataJpaTest는 @Configuration을 자동 로드하지 않으므로 auditing 설정을 명시적으로 켠다.
// 이 클래스의 엔티티(AuditingProbe 등)는 실제 스키마(Flyway 마이그레이션)에 없는 테스트 전용 엔티티라
// ddl-auto를 이 클래스에서만 create-drop으로 풀고 Flyway를 꺼서 즉석으로 테이블을 만든다.
@DataJpaTest
@Import({JpaAuditingConfig.class, TestcontainersConfig.class})
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
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

    // 테스트 전용 엔티티 — 이 클래스에서만 ddl-auto: create-drop + flyway 비활성화라 흔적이 남지 않는다
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

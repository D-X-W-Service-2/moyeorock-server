# Flyway Migration — DB 마이그레이션 컨벤션 (MySQL 8.0)

> Flyway 마이그레이션 파일의 위치·네이밍·작성 규칙, FK 제약 정책을 다룬다.
> 테이블/컬럼 추가·변경, 새 도메인 엔티티 작업 시 참조한다.
> 엔티티 작성 규칙은 `docs/conventions/architecture.md` §5, 코드 스타일은 `docs/conventions/conventions.md` §2를 함께 본다.

`billilge/stream-server`의 동명 문서를 참고해 moyeorock 구조(단일 Gradle 모듈, `status` enum 기반 소프트 삭제)에 맞게 조정했다.

---

## 0. 왜 `ddl-auto`를 껐나

기존엔 `spring.jpa.hibernate.ddl-auto: update`로 Hibernate가 엔티티 매핑을 보고 스키마를 자동 생성·갱신했다. 이제 `validate`로 바꾸고 Flyway로 스키마를 직접 관리한다.

- Hibernate가 스키마를 만들면 **엔티티 매핑이 곧 스키마**가 돼서, `@ManyToOne`을 쓰는 순간 실제 DB `FOREIGN KEY` 제약까지 자동으로 생긴다. 도메인 경계를 코드(Service로만 통신)에서는 지키면서 DB 레벨에서는 물리적으로 묶어버리는 모순이 생김.
- 스키마 변경 이력이 코드(엔티티 diff)에만 남고 별도로 추적되지 않는다.
- Flyway로 가면 스키마가 명시적인 SQL 파일로 버전 관리되고, `validate` 모드가 엔티티-스키마 불일치를 배포 전에 잡아준다.

---

## 1. 위치

```
src/main/resources/db/migration/
├── V1__create_group_tables.sql
├── V2__create_notice_table.sql
└── ...
```

---

## 2. 파일 네이밍

- Flyway 기본 규칙: `V{버전}__{설명}.sql` (버전은 순차 증가 정수).
- 설명은 영문 snake_case, 변경 내용을 동사로 시작(`create_{table}_table`, `add_{column}_to_{table}`, `add_index_to_{table}` 등).
- **버전 충돌 주의**: 여러 사람이 동시에 작업할 때 머지 직전 번호 중복을 확인하고, 겹치면 머지하는 쪽에서 재조정한다.

---

## 3. 작성 규칙

### 3-1. 불변성

이미 머지되어 적용된 파일은 **수정하지 않는다.** 변경은 새 버전 파일로 추가한다.

### 3-2. 테이블 생성 & FK 제약 금지

- 테이블명은 복수형 snake_case(`groups`, `group_members`) — `erd.md`와 동일.
- 기본 컬럼(`id`, `created_at`)을 포함하고, `updated_at`이 필요한 테이블(`architecture.md` §5 기준)엔 그것도 포함한다.
- **다른 테이블을 참조하는 컬럼(`group_id`, `user_id` 등)에 `REFERENCES`/`FOREIGN KEY`를 걸지 않는다.** 같은 도메인이 소유한 두 테이블 사이(`groups`↔`group_members`)여도 예외 없다. 참조 무결성은 애플리케이션(Service) 레벨에서 관리하고 DB는 컬럼·인덱스만 둔다.

```sql
-- V1__create_group_tables.sql
CREATE TABLE groups (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    description TEXT,
    type VARCHAR(10) NOT NULL,
    region VARCHAR(50),
    cover_image VARCHAR(500),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);

CREATE TABLE group_members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id BIGINT NOT NULL,   -- FK 아님, 인덱스만
    user_id BIGINT NOT NULL,    -- FK 아님, 인덱스만
    role VARCHAR(10) NOT NULL,
    status VARCHAR(10) NOT NULL,
    joined_at DATETIME(6) NOT NULL
);

CREATE INDEX idx_group_members_group_id ON group_members (group_id);
CREATE UNIQUE INDEX uk_group_members_group_id_user_id ON group_members (group_id, user_id);
```

**엔티티 쪽도 대칭이다** — `@ManyToOne`으로 FK 컬럼을 매핑하지 않고 `Long` 필드로 둔다(`architecture.md` §5, `conventions.md` §2). 필요한 연관 데이터는 해당 도메인 Service 호출로 받는다.

### 3-3. 컬럼 추가/변경

- 운영 데이터가 있는 테이블에 `NOT NULL` 컬럼을 추가할 때는 `DEFAULT`를 지정한다.

```sql
ALTER TABLE groups
    ADD COLUMN member_limit INT NOT NULL DEFAULT 0;
```

### 3-4. 인덱스

- 조회 빈도가 높은 컬럼, WHERE/ORDER BY에 자주 쓰이는 컬럼에 인덱스를 추가한다. 인덱스명은 `idx_{table}_{column...}`, 유니크는 `uk_{table}_{column...}`.
- `erd.md`에 이미 명시된 인덱스·유니크 제약(예: `team_songs.selected_song_id`의 생성 컬럼 유니크, `recruit_posts`의 `(status, region, created_at)` 복합 인덱스)은 그대로 옮긴다.

### 3-5. 소프트 삭제 컬럼

moyeorock은 `deleted_at`이 아니라 **`status` enum 컬럼 기반 소프트 삭제**를 쓴다(`architecture.md` §6). 대부분은 일반 인덱스로 충분하다 — `deleted_at` NULL 기반 부분 유니크 같은 특수 처리가 필요 없다.

단, `team_songs`처럼 "특정 상태일 때만 유일해야" 하는 경우는 `erd.md`에 이미 정의된 MySQL 생성 컬럼 패턴을 그대로 따른다:

```sql
-- erd.md §15 team_songs 기준
ALTER TABLE team_songs
    ADD COLUMN selected_song_id BIGINT
        GENERATED ALWAYS AS (IF(progress = 'SELECTED', song_id, NULL)) STORED;

CREATE UNIQUE INDEX uk_team_songs_performance_id_selected_song_id
    ON team_songs (performance_id, selected_song_id);
```

### 3-6. 데이터 마이그레이션

스키마 변경과 데이터 마이그레이션은 가능하면 별도 파일로 분리한다. 대량 변경은 실행 시간/락 영향을 별도 검토한다.

---

## 4. 테스트 — Testcontainers MySQL

로컬/CI 테스트 전부 **실제 MySQL(Testcontainers)로 통일**한다. H2는 더 이상 쓰지 않는다.

- DB가 필요한 테스트는 `@Import(TestcontainersConfig.class)`를 붙인다 (`src/test/java/com/moyeorock/config/TestcontainersConfig.java`, `@ServiceConnection`으로 datasource 자동 주입).
- `src/test/resources/application.yml`도 `ddl-auto: validate` + Flyway 활성화라 실제 마이그레이션이 그대로 실행된다. `main`과 스키마 관리 방식이 동일해서 "테스트는 통과했는데 실제 배포에서 스키마가 안 맞는" 상황을 줄인다.
- 로컬에 **Docker가 떠 있어야** 테스트가 돌아간다. CI(GitHub Actions `ubuntu-latest`)는 Docker가 기본 설치돼 있어 별도 설정 없이 동작한다.
- 테스트 전용 엔티티(실제 스키마에 없는 것)를 쓰는 슬라이스 테스트는 그 클래스에서만 `spring.jpa.hibernate.ddl-auto=create-drop` + `spring.flyway.enabled=false`로 오버라이드한다 (`BaseEntityAuditingTest` 예시 참고).
- 테스트 실행 속도가 느려지는 트레이드오프가 있다 — 체감되면 다시 논의한다.

---

## 5. JPA Entity와의 관계

- 엔티티 필드 변경은 반드시 대응 마이그레이션과 함께 작성한다.
- `prod`·`dev`·테스트 전부 `ddl-auto: validate` + Flyway. `update`/`create-drop`을 기본값으로 쓰지 않는다(테스트 전용 슬라이스의 명시적 오버라이드는 예외).

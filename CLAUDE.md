# moyeorock

밴드 합주·공연 팀 매칭 플랫폼. Spring Boot 백엔드.

## 스택

- Java 17 · Spring Boot 4.1.0 · Gradle
- MySQL 8.0 · Spring Data JPA · Flyway (스키마는 `ddl-auto`가 아니라 마이그레이션으로 관리, `docs/conventions/flyway-migration.md`)
- Spring Security (JWT) · springdoc-openapi 2.7.0
- Lombok
- 테스트 DB: Testcontainers MySQL (H2 안 씀)

```bash
./gradlew build      # 빌드
./gradlew test       # 테스트
./gradlew bootRun    # 실행
```

필드 제약은 `spring-boot-starter-validation`으로 구현한다(`@Valid` `@NotBlank` `@Size`).

## 구조

`com.moyeorock` 아래 2층. `global/`(공통 인프라) + `domain/`(기능 단위).

기능 하나가 디렉토리 하나다. 도메인 디렉토리 안에 `controller` `service` `repository` `entity` `dto`를 둔다.

```
domain/team/
├── controller/TeamController.java
├── service/TeamService.java
├── repository/TeamRepository.java
├── entity/Team.java
└── dto/
    ├── request/TeamCreateRequest.java
    └── response/TeamDetailResponse.java
```

## 절대 규칙

1. **엔티티는 도메인 밖으로 나가지 않는다.** 컨트롤러 반환값과 도메인 간 전달은 항상 DTO.
2. **다른 도메인은 Service를 통해서만 호출한다.** 남의 Repository·Entity 직접 접근 금지.
3. **계층·추상화를 임의로 추가하지 않는다.** Facade·Manager·Helper·`ServiceImpl` 금지. 필요하다고 판단되면 만들지 말고 물어볼 것.
4. **공용 enum은 `global/common/enums`에만 정의한다.** 도메인에서 같은 이름으로 새로 만들지 않는다.
5. **스키마를 임의로 변경하지 않는다.** `docs/conventions/erd.md`가 기준. 컬럼이 없으면 기능을 빼거나 물어볼 것. 변경 절차는 `docs/conventions/erd.md`의 변경 프로세스를 따른다.
6. **다른 테이블을 참조하는 컬럼은 `@ManyToOne`이 아니라 `Long`으로 매핑한다.** 같은 도메인 소유 테이블 간이어도 예외 없음. DB에도 FK를 걸지 않는다. `docs/conventions/architecture.md` §5, `docs/conventions/flyway-migration.md` §3-2 참고.

## 작업 전 읽을 문서

| 작업 | 읽을 것 |
|---|---|
| 새 도메인·엔드포인트 추가 | `docs/conventions/architecture.md` `docs/conventions/domains.md` `docs/conventions/api-conventions.md` `docs/specs/api-spec.md` |
| 엔티티 작성·수정 | `docs/conventions/erd.md` 해당 테이블, `docs/conventions/flyway-migration.md` |
| DTO 작성 | `docs/conventions/dto-naming.md` 해당 도메인 절, `docs/specs/dto-spec.md` 필드 정의 |
| 기존 코드 수정·버그 | `docs/conventions/conventions.md` |

**엔드포인트 상세 명세(요청·응답 구조)는 `docs/specs/`에 있다** (`api-spec.md` 엔드포인트 지도 · `dto-spec.md` DTO 필드 정의). **정본은 노션 `API 명세서 v0`**이고 `docs/specs/`는 대조 날짜가 명시된 스냅샷이다 — 노션 변경 시 대조해서 갱신한다.

## 작업 방식

- 코드를 쓰기 전에 계획을 먼저 제시하고 승인을 받는다.
- 명세(`docs/specs/`)에 없는 엔드포인트·필드를 임의로 만들지 않는다. 필요해 보이면 제안만 한다.
- 명세에 없거나 비어 있는 항목은 추측하지 말고 요청한다.
- 명세와 `docs/conventions/erd.md`가 어긋나면 그대로 진행하지 말고 알린다.
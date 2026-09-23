---
name: convention
description: 이 프로젝트의 코딩 컨벤션을 적용하는 스킬. 코드를 작성하거나 리뷰할 때 /convention으로 호출하면 docs/conventions/ 문서를 읽고 컨벤션에 맞는 코드를 작성한다.
---

# Convention 적용 워크플로우

코딩 작업 전에 이 워크플로우를 따라 컨벤션을 파악하고 적용한다.

## Step 1 — 인덱스 읽기

반드시 `CLAUDE.md`의 "작업 전 읽을 문서" 표를 먼저 읽는다.
작업 유형별로 참조해야 할 문서가 정리되어 있다. (moyeorock엔 별도 `00-index.md`가 없고 이 표가 그 역할을 한다.)

```
Read: CLAUDE.md
```

## Step 2 — 필요한 문서만 선택적으로 읽기

현재 작업 컨텍스트를 바탕으로 필요한 문서만 읽는다. 모든 문서를 한 번에 읽지 않는다.

| 작업 유형 | 읽을 문서 |
|-----------|-----------|
| 새 도메인·엔드포인트 추가 | `docs/conventions/architecture.md` `docs/conventions/domains.md` `docs/conventions/api-conventions.md` `docs/specs/api-spec.md` |
| Controller / Service / Repository / DTO 작성 | `docs/conventions/conventions.md` |
| 엔티티 작성·수정 | `docs/conventions/erd.md` 해당 테이블, `docs/conventions/flyway-migration.md` |
| DTO 작성 | `docs/conventions/dto-naming.md` 해당 도메인 절, `docs/specs/dto-spec.md` 필드 정의 |
| 새 에러 코드 추가, 예외 처리 | `docs/conventions/api-conventions.md` §3 |
| 기존 코드 수정·버그 | `docs/conventions/conventions.md` |
| git 관련 작업(커밋·브랜치·이슈·PR) | `docs/conventions/git-convention.md` (또는 `/git` 스킬) |

## Step 3 — 컨벤션 적용 및 자가 검증

코드 작성 완료 후 아래 항목을 확인한다.

- [ ] CLAUDE.md 절대 규칙 1~6을 어기지 않았는가 (엔티티가 도메인 밖으로 안 나감·Service 경유 호출·불필요한 계층 추가 없음·공용 enum 위치·스키마 임의 변경 없음·참조 컬럼은 `Long`+FK 없음)
- [ ] 레이어별 클래스 명명·패키지 규칙을 지켰는가 (`docs/conventions/conventions.md`)
- [ ] 의존성 방향이 단방향인가 — 다른 도메인의 Entity·Repository·요청 DTO를 직접 import하지 않았는가 (`docs/conventions/architecture.md`)
- [ ] 에러는 `BusinessException` + `ErrorCode`로 처리했는가 (`docs/conventions/api-conventions.md` §3)
- [ ] DB 변경 시 `docs/conventions/erd.md`를 갱신하고, Flyway 마이그레이션 파일을 함께 작성했는가 (`docs/conventions/flyway-migration.md`)
- [ ] DTO 이름이 `docs/conventions/dto-naming.md`와 일치하는가

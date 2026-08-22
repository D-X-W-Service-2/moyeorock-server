# Git Convention

> 커밋 메시지, 브랜치 전략, PR/이슈 규칙을 다룬다. (언어·프레임워크 무관)

---

## 1. 커밋 메시지

### 1-1. 형식

**일반 커밋**

```
type: 제목
```

**PR Squash Merge 커밋**

```
type: 제목 - #PR번호
```

- **type**: 변경 성격 접두사 (아래 표)
- **제목**: 한글, 명사형/동사 종결형, 마침표 없음
- **PR번호**: Squash Merge 커밋에만 붙인다

### 1-2. Type 종류

| type | 설명 |
| --- | --- |
| `feat` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 기능 변경 없는 코드 개선 |
| `docs` | 문서 추가·수정 |
| `test` | 테스트 코드 추가·수정 |
| `chore` | 빌드 설정, 의존성, 기타 잡무 |
| `init` | 프로젝트·모듈 초기 세팅 |

### 1-3. 예시

```
feat: 사물함 신청 API 추가
fix: 대여 반납일 검증 누락 수정
refactor: RentalServiceImpl 재고 검증 분리
docs: 아키텍처 컨벤션 문서 추가
chore: spring-boot 버전 4.1.0으로 업그레이드
```

```
feat: 사물함 신청 API 추가 - #12
```

---

## 2. 브랜치 전략

### 2-1. 형식

```
{type}/#{이슈번호}-{작업내용}
```

- **type**: 커밋 type과 동일
- **이슈번호**: `#`을 붙인다
- **작업내용**: 영문 소문자·하이픈(`-`)만

### 2-2. 브랜치 종류

| 브랜치 | 설명 |
| --- | --- |
| `main` | 배포 기준 브랜치 |
| `feat/#{이슈번호}-{작업내용}` | 기능 개발 |
| `fix/#{이슈번호}-{작업내용}` | 버그 수정 |
| `refactor/#{이슈번호}-{작업내용}` | 리팩토링 |
| `docs/#{이슈번호}-{작업내용}` | 문서 작업 |
| `chore/#{이슈번호}-{작업내용}` | 빌드·설정 변경 |

### 2-3. 예시

```
feat/#12-locker-application
fix/#34-rental-return-date
refactor/#7-rental-service
docs/#5-git-convention
```

---

## 3. PR 규칙

### 3-1. PR 제목

```
[{Type}/#{이슈번호}] {설명}
```

- **Type**: 커밋 type 첫 글자 대문자 (`Feat`, `Fix`, `Refactor`, `Docs`, `Test`, `Chore`, `Init`)
- **이슈번호**: 반드시 포함
- **설명**: 한글로 간략히

```
[Feat/#12] 사물함 신청 API 추가
[Fix/#34] 대여 반납일 검증 누락 수정
```

Squash Merge 시 커밋 메시지에 `- #PR번호`를 추가한다.

### 3-2. PR 본문

`.github/pull_request_template.md`를 사용한다. 연관 이슈 연결, 작업 내용, 리뷰 요구사항을 기술한다.

### 3-3. Merge 전략

- `Squash and Merge`를 기본으로 사용한다.

### 3-4. 기타 규칙

- `main`에 직접 push하지 않는다.
- PR은 최소 1명 리뷰 승인 후 Merge한다.
- **CI 필수 체크**: 모듈 경계 검증(`ModularityTests.verify()`)을 포함한 `gradle check`가 통과해야 Merge할 수 있다(`architecture.md` 4-1절). `internal` 경계는 이 게이트로만 강제된다.
- Merge 후 작업 브랜치는 삭제한다.

---

## 4. 배포 규칙

- `main` push → `deploy-dev.yml` → **dev 서버** 배포.
- **prod 배포는 GitHub Release 발행(published)으로 트리거** (`deploy-prod.yml`).
    - 릴리스 태그: `vX.Y.Z` (Semantic Versioning)
    - 릴리스 대상 커밋은 dev에 먼저 배포·검증된 `main` 커밋이어야 한다 (Flyway 마이그레이션 순서 보장).
    - 릴리스 노트에 주요 변경(머지된 PR 목록)을 기술한다.

```
PR 머지 → main push → deploy-dev.yml → dev 배포·검증
                                          ↓
                    GitHub Release 발행(vX.Y.Z) → deploy-prod.yml → prod 배포
```

---

## 5. 이슈 규칙

`.github/ISSUE_TEMPLATE/issue-template.md`를 사용한다. 구현 내용을 체크리스트로 작성하고 참고 자료를 첨부한다.

```
이슈 생성 → 브랜치 생성 (이슈번호 포함) → 작업·커밋 → PR 생성 (이슈 연결) → 리뷰 → Merge → 브랜치 삭제
```
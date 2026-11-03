# 아키텍처 규칙

기능(도메인) 단위 디렉토리 + 3계층. 그 이상의 패턴은 쓰지 않는다.

## 1. 패키지 구조

```
com.moyeorock
├── global/
│   ├── config/          OpenApiConfig, SecurityConfig(CORS 포함), WebConfig, JpaAuditingConfig, TimezoneConfig
│   ├── security/        JwtProvider, JwtAuthenticationFilter,
│   │                    AuthUserArgumentResolver, @AuthUser
│   ├── exception/       GlobalExceptionHandler, ErrorCode, BusinessException
│   ├── logging/         RequestLoggingFilter, MdcKeys
│   ├── common/
│   │   ├── entity/      BaseTimeEntity, BaseEntity
│   │   ├── dto/         ApiResponse, PageResponse, DeleteResponse
│   │   └── enums/       Instrument, Level, Genre, Region, TargetType …
│   ├── event/           도메인 간 이벤트 정의
│   └── file/            파일 업로드
└── domain/
    ├── auth/  user/  group/  notice/  performance/  team/
    ├── rehearsal/  recruit/  join/  song/  setlist/
    └── bookmark/  notification/  dashboard/
```

도메인 내부는 전부 동일하게 `controller` `service` `repository` `entity` `dto/request` `dto/response`.

`dashboard`는 엔티티가 없다. `notice`는 `GroupNotice` 하나만 갖는다.

## 2. 계층별 책임

| 계층 | 하는 일 | 하지 않는 일 |
|---|---|---|
| Controller | 요청 매핑, 파라미터 검증(`@Valid`), 인증 주체 주입, 응답 봉투 씌우기 | 비즈니스 로직, 트랜잭션, Repository 직접 호출 |
| Service | 도메인 로직, 권한 검증, 트랜잭션 경계, 엔티티 ↔ DTO 변환 | HTTP·서블릿 타입 참조 |
| Repository | 쿼리 | 로직, 조건 분기 |

**권한 검증은 Service에서 한다.** 컨트롤러에서 하면 같은 검증이 여러 곳에 흩어지고, 다른 도메인이 Service를 호출할 때 검증이 빠진다.

**트랜잭션은 Service에만 붙인다.** 클래스에는 붙이지 않고 메서드마다 정한다 — 쓰기는 `@Transactional`, 여러 쿼리가 하나의 스냅샷이어야 하는 조회만 `@Transactional(readOnly = true)`, 단일 쿼리 조회는 붙이지 않는다. 기준은 `docs/conventions/conventions.md` §3.

## 3. 호출 방향

```
Controller → Service → Repository → DB
```

단방향. 역방향 호출 금지 — Repository가 Service를 부르거나, Service가 Controller를 참조하지 않는다.

### 도메인 간

- **Service → 다른 도메인 Service** 만 허용
- 다른 도메인의 Repository·Entity 직접 접근 **금지**
- 응답 DTO는 도메인 간 참조 **허용** (`TeamDetailResponse`가 `UserSummaryResponse`를 담는 것은 정상)
- 요청 DTO는 재사용하지 않는다 (`PerformanceTeamCreateRequest`를 `TeamCreateRequest`로 대체 금지)

### 정해진 방향

| | 규칙 |
|---|---|
| `auth → user` | 단방향. `AuthService`가 `UserRepository`를 쓰는 것은 허용하되 반대는 금지 |
| `dashboard` | 다른 도메인의 **Service만** 호출. Repository 직접 접근 금지 |
| `notification` | 어떤 도메인도 직접 참조하지 않는다. 이벤트(`global/event/NotificationEvent`)로만 받는다 — 발행은 트랜잭션 안에서, 수신은 커밋 후 별도 트랜잭션 |
| `join` ↔ `team` `group` | 승인 시 멤버 추가는 `TeamService`·`GroupService` 호출 |
| `performance` → `team` | 공연 종료 시 팀 해체는 `TeamService` 호출 |
| `team` · `join` → `performance` | 공연 확인(공연 팀원 = 모임원, 공연 요약)은 `PerformanceMemberService`로만 한다. `PerformanceService`는 `TeamService`를 부르므로 반대 방향으로 부르면 순환이다(#105) |

### 순환 참조가 생기면

Service끼리 서로 부르게 되는 상황이면 설계가 잘못된 것이다. 임의로 이벤트나 중간 계층을 만들어 우회하지 말고 **알릴 것.**

## 4. 만들지 않는 것

아래는 필요해 보여도 만들지 않는다. 필요하다고 판단되면 만들지 말고 물어본다.

| 금지 | 이유 |
|---|---|
| `TeamServiceImpl` 등 인터페이스+구현체 1:1 | 구현체가 하나뿐이면 인터페이스는 파일만 늘린다 |
| Facade · Manager · Helper · Support 계층 | 3계층으로 충분하다. 로직이 커지면 Service를 나눈다 |
| `TeamMapper` 등 매퍼 클래스 | DTO 변환은 응답 DTO의 정적 팩토리 메서드로 (`TeamDetailResponse.from(team)`) |
| Service 안의 별도 Validator 클래스 | 검증은 Service 메서드 안에서 |
| 도메인별 예외 클래스 | `BusinessException` + `ErrorCode` enum만 쓴다 |
| 임의의 `@Async` · 이벤트 · 캐시 도입 | 성능 문제가 실제로 확인된 뒤에. 단 알림 발생 이벤트(`NotificationEvent`)는 명세에 정해진 방식이라 예외다 |
| DTO 상속 · 제네릭 베이스 DTO | `ApiResponse<T>` `PageResponse<T>` 두 개 외에는 쓰지 않는다 |
| 도메인 밖 공용 유틸 클래스 남발 | `global/common`에 이미 있는지 먼저 확인 |

## 5. 엔티티

- 타임스탬프는 `erd.md` 해당 테이블 기준으로 상속을 고른다 (`@EnableJpaAuditing` 필요)
  - `created_at`·`updated_at` 둘 다 있으면 `global/common/entity/BaseEntity`
  - `created_at`만 있으면 `global/common/entity/BaseTimeEntity`
  - 둘 다 없으면 상속하지 않는다
- **`@ManyToOne`·`@OneToMany` 같은 JPA 연관관계 매핑을 쓰지 않는다.** 다른 테이블을 참조하는 컬럼은 같은 도메인 소유 테이블 간이어도 전부 `Long` 필드로 둔다(`group_id`, `user_id` 등). 필요한 연관 데이터는 해당 도메인 Service를 호출해서 받는다. `CascadeType`·`FetchType`·`orphanRemoval` 논의 자체가 필요 없어진다 — 애초에 매핑을 안 하기 때문.
  - **왜**: `ddl-auto: update`가 아니라 Flyway로 스키마를 관리하기로 하면서(`docs/conventions/flyway-migration.md`), 연관관계 매핑이 실제 DB `FOREIGN KEY` 제약으로 이어지는 걸 원천적으로 막기로 했다. FK 제약은 마이그레이션 SQL에서도 걸지 않는다(`flyway-migration.md` §3-2). DDD의 "다른 애그리거트는 ID로만 참조한다" 원칙과 같은 방향이다.
  - `target_id`(다형성)도 원래부터 이 규칙과 같은 방향이었다 — FK 없이 `Long`, 존재 검증은 Service에서.
- `CascadeType.REMOVE` · `orphanRemoval` 쓰지 않는다. 삭제는 명시적으로 (위 규칙으로 사실상 자동으로 지켜진다)
- Setter 금지. 상태 변경은 의미 있는 메서드로 (`team.disband()`, `member.changeRole(role)`)

## 6. 상태 변경과 파생

**soft delete가 기본이다.** `status` 컬럼을 바꾸고, 연쇄되는 값은 가능한 한 조회 시점에 파생시킨다.

예 — 공연 종료:

```
performances.status = DONE
  └─ teams.status = DISBANDED   ← 여기까지만 실제 UPDATE
```

`team_members` `rehearsals` `join_requests`는 **바꾸지 않는다.** 팀이 `DISBANDED`면 조회에서 걸러지기 때문이다.

단, **사용자 본인이 직접 처리해야 하는 가입 신청·초대**(내 신청 목록, 받은 초대 목록)는 해체된 대상이어도 목록에서 걸러 내지 않는다. 대상은 다른 도메인이 소유해 SQL로 거를 수 없고, 후처리로 빼면 페이지 크기·`totalElements`가 어긋나며, 빠진 건은 사용자가 정리할 길이 없다. 대신 `target`에 id만 남기고 거절·취소는 할 수 있게 한다.

대신 **팀을 참조하는 조회에는 `teams.status = 'ACTIVE'` 조건이 반드시 들어가야 한다.** 이 조건이 빠지면 해체된 팀이 목록·활동·대시보드에 남는다. 팀 조회는 `TeamRepository`의 정해진 메서드를 통해서만 한다.

Hard delete는 5곳뿐이다 — 합주, 공지, 북마크, 팀 선택곡(`team_songs`), 공연 셋리스트(`setlists`). 뒤의 둘은 2026-10-30에 추가했다(이슈 #85) — 선택곡 저장·넘기기가 목록 통째 교체라 빠진 행을 지우고, 모임장이 셋리스트에서 뺀 곡도 지운다.

**예외 — 팀 개별 해체 시 그 팀의 셋리스트·선택곡 행을 지운다.** 팀이 `PATCH /v1/teams/{id}/status`로 해체되면 그 팀이 넘긴 `setlists` 행과 그 팀의 `team_songs` 행을 하드 삭제한다(`TeamService → SetlistService.deleteByTeam`, 팀 해체와 같은 트랜잭션). `setlists`는 `UNIQUE(performance_id, song_id)` 때문에, 행이 남아 있으면 같은 공연의 다른 팀이 그 곡을 영영 넘길 수 없다. `team_songs`는 남아 있으면 다른 팀에게 "다른 팀이 이미 골랐어요"가 계속 뜨는데, setlist 도메인은 팀 상태를 직접 볼 수 없어(CLAUDE.md 절대 규칙 2) 해체 팀을 걸러 내기 어렵다. 공연 종료(`DONE`)로 인한 일괄 해체에서는 둘 다 지우지 않는다 — 끝난 공연의 기록을 남긴다(2026-11-02 결정, PR #87 리뷰).

## 7. 알려진 한계 (v0)

의도적으로 두는 것이다. 발견했다고 임의로 고치지 말 것.

**신청·초대 중복은 사전 검증으로만 막는다.** 동시 요청이 정확히 겹치면 `PENDING` 신청이 두 건 생길 수 있다. DB 유니크 제약으로 막으려면 "`PENDING`일 때만 유일" 조건이 필요한데(신청→거절→재신청이 막히면 안 되므로) 생성 컬럼 트릭이 든다. 실사용에서는 프론트의 버튼 비활성화로 대부분 막히므로 v0에서는 넣지 않는다.

**셋리스트가 확정되면 팀은 곡을 더 넘길 수 없다.** 확정 여부는 `performances.setlist_confirmed_at`이고, 확정 뒤 셋리스트로 넘기기(`PUT /v1/performances/{id}/setlist/teams/{teamId}`)는 `409 INVALID_STATE`다. 팀 선택곡(`PUT /v1/teams/{teamId}/songs`)은 서버가 막지 않는다 — 셋리스트는 넘길 때 복사한 행이라 선택곡을 고쳐도 확정 셋리스트가 바뀌지 않기 때문이다. 확정 뒤 곡 추가는 UI에서 막는다. 모임장은 확정 뒤에도 다시 수정·확정할 수 있고, 팀이 다시 넘기게 하려면 확정을 해제한다(`PATCH /v1/performances/{id}/setlist/unconfirm` — 확정 시각과 `sort_order`를 비운다. 끝났거나 취소된 공연은 해제할 수 없다, 2026-11-02 추가). (2026-10-30 이슈 #85로 바뀌었다 — 이전에는 `team_songs.progress = SELECTED`가 확정을 뜻해서 팀이 그 곡을 고치지 못하게 `409 SETLIST_LOCKED`로 막았다.)
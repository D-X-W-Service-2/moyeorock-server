# 아키텍처 규칙

기능(도메인) 단위 디렉토리 + 3계층. 그 이상의 패턴은 쓰지 않는다.

## 1. 패키지 구조

```
com.moyeorock
├── global/
│   ├── config/          SwaggerConfig, CorsConfig, WebConfig, JpaAuditingConfig
│   ├── security/        JwtProvider, JwtAuthenticationFilter,
│   │                    AuthUserArgumentResolver, @AuthUser
│   ├── exception/       GlobalExceptionHandler, ErrorCode, BusinessException
│   ├── common/
│   │   ├── entity/      BaseEntity
│   │   ├── dto/         ApiResponse, PageResponse, ErrorResponse, DeleteResponse
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

**트랜잭션은 Service에만 붙인다.** 클래스에 `@Transactional(readOnly = true)`, 쓰기 메서드에만 `@Transactional`.

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
| `notification` | 어떤 도메인도 직접 참조하지 않는다. 이벤트로만 받는다 |
| `join` ↔ `team` `group` | 승인 시 멤버 추가는 `TeamService`·`GroupService` 호출 |
| `performance` → `team` | 공연 종료 시 팀 해체는 `TeamService` 호출 |

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
| 임의의 `@Async` · 이벤트 · 캐시 도입 | 성능 문제가 실제로 확인된 뒤에 |
| DTO 상속 · 제네릭 베이스 DTO | `ApiResponse<T>` `PageResponse<T>` 두 개 외에는 쓰지 않는다 |
| 도메인 밖 공용 유틸 클래스 남발 | `global/common`에 이미 있는지 먼저 확인 |

## 5. 엔티티

- `global/common/entity/BaseEntity`를 상속해 `createdAt` `updatedAt`을 얻는다 (`@EnableJpaAuditing` 필요)
- 연관관계는 **전부 `LAZY`**
- `CascadeType.REMOVE` · `orphanRemoval` 쓰지 않는다. 삭제는 명시적으로
- 양방향 연관관계는 꼭 필요할 때만. 기본은 단방향 `@ManyToOne`
- `target_id`(다형성)는 FK가 없다. 연관관계로 매핑하지 말고 `Long` 그대로 두고 존재 검증은 Service에서
- Setter 금지. 상태 변경은 의미 있는 메서드로 (`team.disband()`, `member.changeRole(role)`)

## 6. 상태 변경과 파생

**soft delete가 기본이다.** `status` 컬럼을 바꾸고, 연쇄되는 값은 가능한 한 조회 시점에 파생시킨다.

예 — 공연 종료:

```
performances.status = DONE
  └─ teams.status = DISBANDED   ← 여기까지만 실제 UPDATE
```

`team_members` `rehearsals` `join_requests`는 **바꾸지 않는다.** 팀이 `DISBANDED`면 조회에서 걸러지기 때문이다.

대신 **팀을 참조하는 조회에는 `teams.status = 'ACTIVE'` 조건이 반드시 들어가야 한다.** 이 조건이 빠지면 해체된 팀이 목록·활동·대시보드에 남는다. 팀 조회는 `TeamRepository`의 정해진 메서드를 통해서만 한다.

Hard delete는 3곳뿐이다 — 합주, 공지, 북마크.

## 7. 알려진 한계 (v0)

의도적으로 두는 것이다. 발견했다고 임의로 고치지 말 것.

**신청·초대 중복은 사전 검증으로만 막는다.** 동시 요청이 정확히 겹치면 `PENDING` 신청이 두 건 생길 수 있다. DB 유니크 제약으로 막으려면 "`PENDING`일 때만 유일" 조건이 필요한데(신청→거절→재신청이 막히면 안 되므로) 생성 컬럼 트릭이 든다. 실사용에서는 프론트의 버튼 비활성화로 대부분 막히므로 v0에서는 넣지 않는다.

**확정된 셋리스트 곡의 상태는 팀이 바꿀 수 없다.** `team_songs.selected_song_id`는 `progress = SELECTED`일 때만 값을 갖는 생성 컬럼이라, 팀이 `SELECTED` → `PRACTICING`으로 바꾸면 공연 내 곡 중복 방지가 풀린다. 따라서 `PUT /v0/teams/{id}/setlist`는 **`SELECTED` 곡의 제거와 상태 변경을 모두 거부한다**(`409 SETLIST_LOCKED`). 해제는 모임장이 `PUT /v0/performances/{id}/setlist`로만 한다.
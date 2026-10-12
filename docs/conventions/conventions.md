# 코드 컨벤션

이 문서의 모든 항목은 팀 합의를 거친 확정 규칙이다. 예외가 필요하면 임의로 어기지 말고 물어본다.

## 1. Lombok

프로젝트에 Lombok이 포함돼 있다. 사용 범위를 좁힌다.

| 쓴다 | 쓰지 않는다 |
|---|---|
| `@Getter` | `@Setter` — 엔티티 상태는 의미 있는 메서드로 바꾼다 |
| `@NoArgsConstructor(access = PROTECTED)` | `@Data` — `equals`/`hashCode`가 엔티티에서 위험하다 |
| `@RequiredArgsConstructor` (Service·Controller 주입) | `@AllArgsConstructor` |
| `@Slf4j` | |

DTO는 `record`라 Lombok이 필요 없다.

### `@Builder`

엔티티에 붙이는 것은 허용하되, **프로덕션 코드에서는 직접 호출하지 않는다.**

| 위치 | 사용 |
|---|---|
| 엔티티 생성 (프로덕션) | ❌ 정적 팩토리만 (`Team.create(...)`) |
| 정적 팩토리 **내부** | ⭕ |
| 테스트 픽스처 | ⭕ |

`@Builder`는 모든 필드를 선택적으로 만들어 `Team.builder().build()`가 통과한다. 필수 값 강제는 정적 팩토리의 파라미터로 한다.

### 정적 팩토리 네이밍

이름 3개를 구분해서 쓴다. 같은 일을 하는 메서드가 클래스마다 다른 이름을 갖지 않게 하는 것이 목적이다.

| 이름 | 쓰는 때 | 기준 | 예시 |
|---|---|---|---|
| `create` | 새 도메인 객체를 **태어나게** 할 때 | 반환값이 DB에 저장될 새 엔티티다. 아직 id가 없고, 생성 시점에만 정해지는 기본값(`status = ACTIVE`, `joinedAt = now()`)을 안에서 채운다 | `Team.create(...)` `GroupMember.create(...)` |
| `from` | 인자 **1개**를 그대로 다른 표현으로 옮길 때 | 입력 하나 → 출력 하나. 정보를 더하지도 빼지도 않는 표현 변환이고, 변환 규칙이 그 인자 안에 다 있다 | `TeamDetailResponse.from(team)` `PageResponse.from(page)` |
| `of` | 여러 조각을 **조립**할 때 | 인자 2개 이상을 합쳐 하나를 만든다. "이 값들로 구성한다"는 뜻 | `GroupDetailResponse.of(group, owner, memberCount, myRole)` |

경계가 애매하면:

- 인자가 1개면 `from`, 2개 이상이면 `of`. **단, 새 엔티티를 만드는 것이면 인자 수와 무관하게 `create`다**
- 다른 도메인의 응답 DTO를 받아 합치는 응답(`GroupMemberResponse`가 `UserSummaryResponse` + 내 엔티티 값을 합치는 경우)은 `from(entity)` 하나로 만들 수 없다 → `of`
- `DeleteResponse.of(id)`처럼 인자 1개인데 `of`인 기존 공용 DTO가 있다. 신규 코드는 위 규칙을 따르고 이미 머지된 것은 건드리지 않는다

## 2. 엔티티

```java
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Team extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "performance_id")
    private Long performanceId;

    @Enumerated(EnumType.STRING)
    private TeamStatus status;

    public static Team create(String name, Region region) { ... }

    public void disband() {
        this.status = TeamStatus.DISBANDED;
    }
}
```

- PK는 `BIGINT AUTO_INCREMENT` → `IDENTITY`
- **다른 테이블 참조 컬럼은 `@ManyToOne` 대신 `Long` 필드로 둔다** — `performance` 도메인이 다른 팀 소유든, 같은 도메인 소유든 예외 없다. DB에도 FK를 안 건다(`docs/conventions/flyway-migration.md` §3-2, `architecture.md` §5). 필요한 값은 `PerformanceService`처럼 소유 도메인의 Service를 호출해서 받는다
- enum은 **반드시 `@Enumerated(EnumType.STRING)`**. `ORDINAL`은 값 순서가 바뀌면 데이터가 깨진다
- 컬럼이 `VARCHAR`로 정의돼 있으므로 DB에는 문자열이 들어간다
- JSON 컬럼(`users.genres` `recruit_posts.wanted_slots` `songs.difficulty`)은 컨버터로 매핑
- 상태 변경은 `disband()` 같은 메서드로. Setter 금지

### 값 객체(VO)로 묶는 기준

**"같이 전달되는 값"과 "같은 의미인 값"은 다르다.** 전자는 DTO가 할 일이고, VO로 묶는 것은 후자뿐이다.

VO로 만든다 — 아래를 **전부** 만족할 때:

1. 두 값이 **항상 함께 바뀐다** (하나만 바뀌는 상황이 성립하지 않는다)
2. 두 값을 합쳐야 **의미가 완성된다** — 합친 것에 이름을 붙일 수 있다
3. 그 묶음을 받는 **연산이나 규칙이 있다** (검증·비교·포맷·포함 판정 등)

```java
// ⭕ 전화번호 앞자리 + 뒷자리 = 전화번호 하나. 합쳐서 포맷·검증한다
// ⭕ 시작 시각 + 종료 시각 = 기간. "시작 < 종료" 규칙과 overlaps() 연산이 붙는다
record Period(LocalDateTime startAt, LocalDateTime endAt) { ... }
```

VO로 만들지 않는다:

| 묶고 싶어지는 것 | 왜 아닌가 |
|---|---|
| `name` + `description` + `region` + `coverImage` | 같이 **전달될** 뿐이다. `GroupUpdateRequest`가 이미 그 자리다 |
| `groupId` + `userId` (유니크 키) | 합친 값에 의미도 연산도 없다. 파라미터 2개로 충분하다 |
| `role` + `status` | **독립적으로 바뀐다**(역할 변경 ≠ 상태 변경). 함께 변하지 않으면 한 값이 아니다 |
| `page` + `size` | Spring `Pageable`이 이미 그 VO다 |

판단이 서지 않으면 만들지 말고 물어본다(`architecture.md` §4 "필요해 보여도 만들지 않는 것").

## 3. Service

```java
@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;

    @Transactional
    public TeamDetailResponse create(Long userId, TeamCreateRequest request) { ... }

    // 팀 + 팀원 수 + 내 역할을 쿼리 여러 번으로 모아 한 화면을 만든다 → 스냅샷 필요
    @Transactional(readOnly = true)
    public TeamDetailResponse getDetail(Long userId, Long teamId) { ... }

    // 단일 쿼리 — 트랜잭션을 새로 열 이유가 없다
    public PageResponse<TeamSummaryResponse> search(TeamSearchCondition condition, Pageable pageable) { ... }
}
```

**클래스에 `@Transactional`을 붙이지 않는다.** 메서드마다 아래 기준으로 정한다.

| 메서드 | 어노테이션 |
|---|---|
| 쓰기(저장·수정·삭제)가 있다 | `@Transactional` |
| 조회인데 **쿼리 2번 이상의 결과가 서로 맞아야 한다** | `@Transactional(readOnly = true)` |
| 조회인데 단일 쿼리다 | **붙이지 않는다** |
| 조회인데 중간에 **다른 도메인 Service 호출**이 섞인다 | **붙이지 않는다** (아래) |

판단 기준은 "조회냐 쓰기냐"가 아니라 **"하나의 스냅샷으로 묶여야 하는가"**다. 예를 들어 모임 상세는 모임·모임원 수·내 역할·고정 공지를 각각 조회해 한 응답으로 만드는데, 그 사이에 탈퇴가 커밋되면 `memberCount`와 목록이 어긋난 채로 내려간다 — 이때는 `readOnly = true`가 필요하다. 반대로 단건 조회 하나는 묶을 게 없다.

**`readOnly = true`를 모든 조회에 붙이지 않는 이유**

- **신호가 죽는다.** 전부 붙어 있으면 "이 조회는 스냅샷이 필요하다"는 표시가 사라지고, 정작 필요한 메서드를 구분할 수 없다
- **공짜가 아니다.** 커넥션을 메서드 끝까지 잡고, 영속성 컨텍스트를 만들고, flush 모드를 바꾼다
- **쓰기 방지 장치가 아니다.** Hibernate의 flush를 생략할 뿐이고, JDBC 레벨 차단은 드라이버·DB 설정에 달려 있다. "실수로 쓰는 걸 막아준다"고 기대하면 안 된다

**다른 도메인 Service 호출이 섞인 조회에 트랜잭션을 걸지 않는 이유** — 호출된 Service가 자기 기준으로 트랜잭션을 열고 닫으므로, 바깥에서 감싸도 두 도메인의 데이터가 같은 시점이라는 보장이 생기지 않는다. 묶이지 않는 것을 묶인 것처럼 적어두면 나중에 오해를 만든다.

- 첫 파라미터는 인증 주체(`Long userId`)
- 반환은 항상 DTO. 엔티티를 반환하지 않는다
- 조회 실패는 `orElseThrow(() -> new BusinessException(ErrorCode.XXX_NOT_FOUND))`
- 트랜잭션을 안 붙인 메서드도 트랜잭션 안에서 호출되면 그 트랜잭션에 참여한다(`Propagation.REQUIRED` 기본값). "안 붙임"은 "단독 호출 시 새로 열지 않는다"는 뜻이다

## 4. Repository

- `JpaRepository<Team, Long>` 상속
- 메서드 이름이 길어지면(조건 3개 초과) `@Query` 또는 QueryDSL. **QueryDSL은 아직 도입 안 함**
- 연관관계 매핑을 안 쓰므로 `@EntityGraph`/`join fetch`로 N+1을 풀 일이 없다. 대신 여러 건의 참조 ID를 한 번에 다른 도메인 Service에 넘겨 일괄 조회하는 방식으로 N+1을 피한다(예: `UserService.getSummaries(List<Long> userIds)` · `getInstruments(Collection<Long> userIds)` · 자기 범위 안에서 닉네임으로 거르는 `filterByNickname(Collection<Long> userIds, String keyword)`) — 단건씩 반복 호출하지 않는다
- 팀 조회 메서드에는 `status = ACTIVE` 조건을 넣는다 (`docs/conventions/architecture.md` §6)

## 5. 네이밍

| 대상 | 규칙 |
|---|---|
| 클래스 | `Team` `TeamService` `TeamController` `TeamRepository` |
| 요청 DTO | `{Entity}{Action}Request` |
| 응답 DTO | `{Entity}{Shape}Response` |
| 테이블·컬럼 | `snake_case` 복수형 (`team_members`) |
| 필드 | `camelCase` — JPA가 자동 변환 |
| enum 상수 | `UPPER_SNAKE` |
| 불리언 필드 | `isRecommendable` — DB는 `is_recommendable` |

## 6. 패키지 접근

같은 도메인 안에서는 제한 없다. 다른 도메인에서 import할 수 있는 것은 **Service와 응답 DTO뿐**이다. Entity·Repository·요청 DTO를 import하고 있으면 잘못된 것이다. **예외 없음** — 이전엔 `@ManyToOne` 연관관계로 다른 도메인 엔티티를 참조하는 것을 예외로 허용했지만, 연관관계 매핑 자체를 안 쓰기로 하면서(`architecture.md` §5) 이 예외도 사라졌다.

## 7. 테스트

- 위치는 `src/test/java` 아래 동일 패키지
- **메서드명은 영문.** 설명은 `@DisplayName`에 한글로 쓴다
- Service 단위 테스트 우선. Controller는 주요 흐름만
- 통합 테스트는 `@SpringBootTest`, 슬라이스는 `@DataJpaTest` / `@WebMvcTest`

```java
@Test
@DisplayName("초대를 보낸 리더는 자기 초대를 수락할 수 없다")
void accept_fails_when_caller_is_inviter() {
    assertThatThrownBy(() -> invitationService.accept(leaderId, invitationId))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NO_PERMISSION);
}
```

### 실패 케이스도 쓴다

권한·상태 검증은 성공 경로만 테스트하면 통과해버린다. 특히 `join` 도메인의 "신청자가 자기 신청을 승인", "리더가 자기 초대를 수락"은 빠뜨리면 상대 동의 없이 팀원이 추가된다. 기대하는 `ErrorCode`까지 단언한다.

`ErrorCode` 정의 방법은 `docs/conventions/api-conventions.md` §3.

## 8. Git · PR

저장소: `D-X-W-Service-2/moyeorock-server`

### 브랜치

`{type}/#{이슈번호}-{작업}` — 예: `feat/#12-team-create`

### PR 제목

`[{Type}/#{이슈번호}] 작업 내용` — 예: `[Docs/#1] 프레젠테이션 레이어 분리 축 컨벤션 문서화`

| Type | 용도 |
|---|---|
| `Feat` | 기능 추가 |
| `Fix` | 버그 수정 |
| `Refactor` | 동작 변화 없는 구조 개선 |
| `Docs` | 문서 |
| `Test` | 테스트 |
| `Chore` | 설정·빌드·의존성 |

### PR 본문

`.github/PULL_REQUEST_TEMPLATE.md` 양식을 따른다.

```markdown
#️⃣연관된 이슈
> ex) #이슈번호, #이슈번호

📝작업 내용
> 이번 PR에서 작업한 내용을 간략히 설명해주세요

스크린샷 (선택)

💬리뷰 요구사항(선택)
> 리뷰어가 특별히 봐주었으면 하는 부분이 있다면 작성해주세요
> ex) 메서드 XXX의 이름을 더 잘 짓고 싶은데 혹시 좋은 명칭이 있을까요?

🛠️기술 선택 및 대안
> 기존 컨벤션에 없던 새 라이브러리·통신 방식·설계 패턴을 선택했다면 무엇을·왜·대안이 뭐였는지 적어주세요. 없으면 "해당 없음"
```

### 머지

**Approve 2명 이상**이어야 머지한다. 셀프 머지 금지.

## 9. 하지 말 것

- 명세에 없는 필드를 응답에 추가
- `System.out.println` — `@Slf4j` 사용
- 주석으로 코드 남기기 — 지운다
- 사용하지 않는 import·변수
- 매직 넘버 — 상수로
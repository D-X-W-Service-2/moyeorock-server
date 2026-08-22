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

## 2. 엔티티

```java
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Team extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performance_id")
    private Performance performance;

    @Enumerated(EnumType.STRING)
    private TeamStatus status;

    public static Team create(String name, Region region) { ... }

    public void disband() {
        this.status = TeamStatus.DISBANDED;
    }
}
```

- PK는 `BIGINT AUTO_INCREMENT` → `IDENTITY`
- enum은 **반드시 `@Enumerated(EnumType.STRING)`**. `ORDINAL`은 값 순서가 바뀌면 데이터가 깨진다
- 컬럼이 `VARCHAR`로 정의돼 있으므로 DB에는 문자열이 들어간다
- JSON 컬럼(`users.genres` `recruit_posts.wanted_slots` `songs.difficulty`)은 컨버터로 매핑
- 상태 변경은 `disband()` 같은 메서드로. Setter 금지

## 3. Service

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamService {

    private final TeamRepository teamRepository;

    @Transactional
    public TeamDetailResponse create(Long userId, TeamCreateRequest request) { ... }
}
```

- 클래스에 `readOnly = true`, 쓰기 메서드에만 `@Transactional`
- 첫 파라미터는 인증 주체(`Long userId`)
- 반환은 항상 DTO. 엔티티를 반환하지 않는다
- 조회 실패는 `orElseThrow(() -> new BusinessException(ErrorCode.XXX_NOT_FOUND))`

## 4. Repository

- `JpaRepository<Team, Long>` 상속
- 메서드 이름이 길어지면(조건 3개 초과) `@Query` 또는 QueryDSL. **QueryDSL은 아직 도입 안 함**
- N+1이 예상되는 조회는 `@EntityGraph` 또는 `join fetch`
- 팀 조회 메서드에는 `status = ACTIVE` 조건을 넣는다 (`docs/architecture.md` §6)

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

같은 도메인 안에서는 제한 없다. 다른 도메인에서 import할 수 있는 것은 **Service와 응답 DTO뿐**이다. Entity·Repository·요청 DTO를 import하고 있으면 잘못된 것이다.

단, 엔티티 필드의 `@ManyToOne` 연관관계로 다른 도메인 엔티티 타입을 참조하는 것은 예외다 (`docs/architecture.md` §3 참고).

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

`ErrorCode` 정의 방법은 `docs/api-conventions.md` §3.

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
```

### 머지

**Approve 2명 이상**이어야 머지한다. 셀프 머지 금지.

## 9. 하지 말 것

- 명세에 없는 필드를 응답에 추가
- `System.out.println` — `@Slf4j` 사용
- 주석으로 코드 남기기 — 지운다
- 사용하지 않는 import·변수
- 매직 넘버 — 상수로
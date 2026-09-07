# API 규약

## 1. 응답 봉투

모든 응답은 `ApiResponse<T>`로 감싼다. 예외 없다.

```json
{ "success": true, "data": { }, "error": null }
{ "success": false, "data": null,
  "error": { "code": "TEAM_NOT_FOUND", "message": "존재하지 않는 팀입니다.", "fieldErrors": [] } }
```

JSON 키는 `camelCase`로 쓴다 (`fieldErrors`, `totalPages`). Jackson 기본 동작 그대로이며 `spring.jackson.property-naming-strategy`를 설정하지 않는다.

컨트롤러 반환 타입은 `ApiResponse<T>`. `ResponseEntity`는 `Location` 헤더가 필요한 생성 응답에서만 쓴다.

## 2. 상태 코드

| 코드 | 사용 |
|---|---|
| 200 | 조회 · 수정 · 삭제 · 상태 전이 |
| 201 | 생성 |
| 400 | `VALIDATION_FAILED` |
| 401 | `UNAUTHORIZED` |
| 403 | `FORBIDDEN` · `NO_PERMISSION` |
| 404 | `..._NOT_FOUND` |
| 409 | 중복 · 상태 충돌 |

**204를 쓰지 않는다.** 봉투를 항상 내려주려면 바디가 있어야 한다. 삭제도 200 + `DeleteResponse`.

## 3. 예외 · ErrorCode

도메인별 예외 클래스를 만들지 않는다. `BusinessException` 하나 + `ErrorCode` enum으로 처리한다.
```
global/exception/
├── ErrorCode.java
├── BusinessException.java
└── GlobalExceptionHandler.java
```

### `ErrorCode`

`HttpStatus` + 코드 문자열 + 기본 메시지를 한 상수에 묶는다. **코드 문자열은 API 명세에 적힌 것을 그대로 쓴다.**

```java
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    VALIDATION_FAILED(BAD_REQUEST, "입력값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    NO_PERMISSION(FORBIDDEN, "권한이 없습니다."),
    INVALID_STATE(CONFLICT, "처리할 수 없는 상태입니다."),

    // user
    USER_NOT_FOUND(NOT_FOUND, "존재하지 않는 사용자입니다."),
    NICKNAME_DUPLICATED(CONFLICT, "이미 사용 중인 닉네임입니다."),

    // team
    TEAM_NOT_FOUND(NOT_FOUND, "존재하지 않는 팀입니다."),
    NOT_TEAM_LEADER(FORBIDDEN, "팀장만 가능합니다."),
    LEADER_MUST_DELEGATE_FIRST(CONFLICT, "팀장을 위임한 뒤 탈퇴할 수 있습니다."),

    // join
    JOIN_REQUEST_NOT_FOUND(NOT_FOUND, "존재하지 않는 신청입니다."),
    JOIN_REQUEST_DUPLICATED(CONFLICT, "이미 신청한 대상입니다."),
    ALREADY_MEMBER(CONFLICT, "이미 소속된 멤버입니다.");

    private final HttpStatus status;
    private final String message;
}
```

**규칙**

- 한 파일에 도메인별로 주석으로 묶어 정의한다. 도메인마다 enum을 나누지 않는다
- 상수명 = 응답 `code` 값. 별도 필드를 두지 않는다
- 여러 도메인이 쓰는 것(`NO_PERMISSION` `INVALID_STATE`)은 공통 구역에
- 새 코드는 **명세에 있는 것만** 추가한다. 명세에 없는 상황이 나오면 코드를 만들지 말고 알린다

```java
throw new BusinessException(ErrorCode.TEAM_NOT_FOUND);
```

### `GlobalExceptionHandler`

| 잡는 예외 | 응답 |
|---|---|
| `BusinessException` | `ErrorCode`의 status·code·message |
| `MethodArgumentNotValidException` | 400 `VALIDATION_FAILED` + `fieldErrors` 채움 |
| `AccessDeniedException` | 403 `NO_PERMISSION` |
| `Exception` | 500. 스택트레이스는 로그에만, 응답에 노출 금지 |

**DB 제약 위반을 그대로 흘리지 않는다.** `UNIQUE` 충돌은 사전 검증으로 409를 내린다. 어느 값이 중복인지 클라이언트가 알아야 한다.

## 4. 페이징

```json
{ "content": [], "page": 0, "size": 20,
  "totalElements": 137, "totalPages": 7, "last": false }
```

Spring의 `Page<T>`를 그대로 반환하지 않는다. 불필요한 필드가 많고 버전에 따라 구조가 바뀐다. `PageResponse.from(page)`로 변환한다.

**쓰는 기준** — 개수에 구조적 상한이 없으면 페이징한다.

| `PageResponse` | 배열이 든 객체 |
|---|---|
| 팀 목록, 공고, 신청·초대, 모임원, 공지, 북마크, 사용자 검색, 곡 검색, 공연 목록, **알림** | 팀원(밴드 편성), 합주(기간 제한), 세션 |

`size` 상한을 건다 (`spring.data.web.pageable.max-page-size`).

알림은 삭제 기능이 없어 무한히 쌓이므로 페이징한다. 다만 배지 숫자(`unreadCount`)가 필요해 `NotificationsResponse` 안에 `PageResponse<NotificationResponse>`를 중첩한다.

## 5. URL

- 접두사 `/v0/`
- 리소스는 복수형 (`/teams` `/join-requests`)
- 상태 전이는 `PATCH /{id}/status` 또는 `PATCH /{id}/{action}`
- 하위 리소스는 부모 경로 아래 (`/teams/{teamId}/rehearsals`), 단건 조회·수정·삭제는 단독 경로 (`/rehearsals/{id}`)
- `me` 별칭 지원 — `?authorId=me`

**소프트 삭제는 `DELETE`가 아니라 `PATCH .../status`다.** `DELETE`는 실제로 행이 사라지는 3곳(합주·공지·북마크)에만 쓴다. 팀원·모임원의 탈퇴·강퇴는 행이 사라지지 않고 `status`만 바뀌므로 `PATCH /{teamId 또는 groupId}/members/{userId}/status`를 쓴다.

## 6. DTO

명명 규칙 전체는 `docs/conventions/dto-naming.md`. 핵심만:

1. `record`로 선언
2. 생성·수정 요청은 항상 분리
3. 응답은 재사용 — `TeamCreateResponse` 같은 건 만들지 않고 `TeamDetailResponse`를 쓴다
4. 페이징 목록에 전용 DTO 금지 — `PageResponse<TeamSummaryResponse>`
5. 한 곳에서만 쓰는 항목은 중첩 `record`
6. 변환은 응답 DTO의 정적 팩토리 (`TeamDetailResponse.from(team)`)

## 7. 인증

`@AuthUser`로 인증 주체를 주입받는다. `SecurityContextHolder`를 직접 쓰지 않는다.

```java
@GetMapping("/v0/users/me")
public ApiResponse<UserMeResponse> getMe(@AuthUser Long userId) { ... }
```

비로그인 허용 엔드포인트에서는 `null`이 들어온다. 명세의 `myRole` `canEdit` `myJoinRequest` 같은 필드가 이 값에 따라 달라진다.

## 8. 날짜

ISO-8601 문자열. `LocalDateTime`으로 받고 보낸다. 타임존 변환은 하지 않는다.

## 9. Swagger

`@Operation(summary = ...)` 정도만 붙인다. 요청·응답 스키마는 DTO에서 자동 생성되므로 `@ApiResponses`를 장황하게 달지 않는다.
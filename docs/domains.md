# 도메인 지도

14개 도메인 · 72개 엔드포인트 · 16개 테이블.

## 전체

| 도메인 | 담당 | 소유 테이블 | 개수 |
|---|---|---|---|
| `auth` | 1팀 | (없음 · `users` 읽기) | 3 |
| `user` | 1팀 | `users` `user_instruments` | 9 |
| `team` | 2팀 | `teams` `team_genres` `team_members` | 9 |
| `rehearsal` | 2팀 | `rehearsals` | 6 |
| `recruit` | 3팀 | `recruit_posts` | 5 |
| `join` | 3팀 | `join_requests` | 12 |
| `notification` | 3팀 | `notifications` | 2 |
| `bookmark` | 3팀 | `bookmarks` | 3 |
| `group` | 4팀 | `groups` `group_members` | 6 |
| `notice` | 4팀 | `group_notices` | 4 |
| `performance` | 4팀 | `performances` | 7 |
| `song` | 4팀 | `songs` | 2 |
| `setlist` | 4팀 | `team_songs` | 2 |
| `dashboard` | 3팀 | (없음) | 1 |

> **엔드포인트 상세 명세(요청·응답 구조·에러 코드)는 이 저장소에 없다.** 작업 지시와 함께 전달된다.
> 주어지지 않았으면 **임의로 만들지 말고 요청할 것.** 응답 필드나 에러 코드를 추측해서 채우지 않는다.
> DTO 이름은 `docs/dto-naming.md`, 컬럼은 `docs/erd.md`에 있다.

`global/file`은 도메인이 아니고 엔드포인트 1개를 갖는다. **1팀 담당** — Security와 같은 인프라 성격이고, 1팀(프로필)·4팀(커버·포스터)이 함께 쓴다.

## 헷갈리는 경계

**`join`은 컨트롤러가 2개다.** `JoinRequestController`(`/v0/join-requests`)와 `InvitationController`(`/v0/invitations`). URI와 컨트롤러는 나뉘지만 **엔티티는 `JoinRequest` 하나, 테이블도 `join_requests` 하나**다. `direction`(`APPLY` / `INVITE`)으로 구분한다.

- 별도 `Invitation` 엔티티나 `invitations` 테이블을 **만들지 않는다**
- 조회 시 `direction` 조건을 항상 포함한다. id 시퀀스를 공유하므로 빠뜨리면 신청 id로 초대 API를 호출하는 게 통과한다
- **단, 중복 검사에서만 `direction`을 뺀다.** 신청과 초대가 동시에 있으면 가입 경로가 둘 생기므로 양쪽을 같이 봐야 한다

**`notice`는 `group`과 분리한다.** 테이블이 `group_notices`이고 엔티티는 `GroupNotice`. `Post`라는 이름을 쓰지 않는다. 경로는 생성·목록만 `/v0/groups/{id}/notices`이고 수정·삭제는 `/v0/notices/{id}`다.

**`setlist`가 `team_songs`를 소유한다.** `song`은 곡 마스터(`songs`)만 갖는다. `team_songs`를 쓰는 엔드포인트는 `PUT /v0/teams/{id}/setlist`와 `PUT /v0/performances/{id}/setlist` 두 개인데, **둘 다 `setlist` 도메인 소유**다. `performance`도 `team`도 `team_songs`를 직접 건드리지 않는다.

한 곳이 소유해야 하는 이유는 `UNIQUE (performance_id, selected_song_id)` 제약 때문이다. 두 서비스가 같은 테이블에 쓰면 제약 위반을 어디서 검증할지 갈린다. DTO 이름도 `SetlistConfirmRequest` · `SetlistResponse`로 통일한다(`Performance...` 접두사를 쓰지 않는다).

**`user/me` 경로는 user 도메인이 아니다.** `/v0/users/me/rehearsals`는 `rehearsal`, `/v0/users/me/join-requests`는 `join`, `/v0/users/me/songs/recommendations`는 `song` 소유다. "내 것만 필터링한 뷰"일 뿐이다.

**공연 참가 단위는 팀이다.** 공연자 개별 관리 API는 없다. 공연에서 팀을 빼는 것 = 팀 해체.

## 공용 enum

`global/common/enums`에만 정의한다. 여러 도메인이 쓰기 때문이다.

| enum | 쓰이는 곳 |
|---|---|
| `Instrument` | `user_instruments` `team_members` `join_requests` `recruit_posts` |
| `Level` | `user_instruments` `songs.difficulty` |
| `Genre` | `users` `teams` `songs` |
| `Region` | `users` `teams` `groups` `recruit_posts` |
| `TargetType` | `recruit_posts` `join_requests` (`TEAM` `GROUP`) |

**값이 다르면 공유하지 않는다.**

- `MemberStatus`(팀) = `ACTIVE` `LEFT` `REMOVED`
- `GroupMemberStatus`(모임) = `ACTIVE` `LEFT` `BANNED`
- `BookmarkTargetType` = `TEAM` `USER` `SONG` — `TargetType`과 다르다
- `NotificationTargetType` — `TargetType`보다 값이 많다

이름이 비슷해서 재사용하고 싶어지는 지점이다. 공유하면 잘못된 값이 검증을 통과한다.

## 담당

| 팀 | 도메인 | 엔드포인트 |
|---|---|---|
| **1팀** | `auth` `user` `file` | 13 |
| **2팀** | `team` `rehearsal` | 15 |
| **3팀** | `recruit` `join` `notification` `bookmark` `dashboard` | 23 |
| **4팀** | `group` `notice` `performance` `song` `setlist` | 21 |

`dashboard`는 2·3·4팀의 Service를 모두 호출하므로 3팀 작업 중 **마지막**에 만든다.

## 팀 간 의존

혼자 끝낼 수 없는 지점이다. 착수 전에 인터페이스를 합의한다.

| 지점 | 누가 → 누구 | 내용 |
|---|---|---|
| `global/common` `enums` `exception` | **1팀 → 전원** | 나머지 셋이 이것 없이는 시작 못 한다. **최우선** |
| Security · `@AuthUser` | **1팀 → 전원** | 주입 타입(`Long userId`)을 먼저 확정 |
| `file` presigned URL | 1팀 → 4팀 | 모임 커버·공연 포스터가 이 API를 기다린다 |
| 신청·초대 승인 | 3팀 → 2팀 `TeamService` · 4팀 `GroupService` | 승인 시 멤버 추가. 3팀이 직접 `team_members`를 만들지 않는다 |
| 공연 내 팀 생성 | 4팀 → 2팀 `TeamService` | `POST /v0/performances/{id}/teams`가 팀을 만든다 |
| 공연 종료 | 4팀 → 2팀 `TeamService` | 상태가 `DONE`이면 소속 팀 해체 |
| 알림 발생 | 2·3·4팀 → 3팀 `notification` | 이벤트로만 전달. `NotificationService` 직접 호출 금지 |
| 대시보드 | 3팀 → 2·4팀 | 각 도메인 Service 호출 |

**2팀의 `TeamService`가 3팀·4팀 양쪽에서 호출된다.** 팀 생성·해체·멤버 추가 메서드 시그니처를 2팀이 먼저 확정하고 공유해야 나머지가 막히지 않는다.

**`PUT /v0/teams/{id}/setlist`는 경로가 `/teams`로 시작하지만 4팀 소유다.** `team_songs`를 다루므로 `setlist` 도메인이고, 2팀의 `TeamController`에 넣지 않는다.

**AI 추천 3개는 2팀·4팀에 나뉘어 있다** — 팀원 추천(2팀), 개인 추천곡·팀 셋리스트 추천(4팀). 룰 기반이냐 외부 모델이냐를 **두 팀이 함께 결정해야 한다.** 한쪽만 폴링 방식이 되면 프론트가 두 패턴을 구현하게 된다.

## 착수 순서

1. 1팀 — `global/common/dto` `enums` `exception`
2. 1팀 — Security · JWT · `@AuthUser`
3. 1팀 — `file` (4팀 포스터·커버가 기다린다)
4. 2팀 — `TeamService` 시그니처 확정 후 공유
5. 2·3·4팀 병렬
6. 3팀 — `dashboard` (다른 도메인이 끝난 뒤)

3팀은 `recruit`(5개)부터 시작하는 게 좋다. 다른 도메인 의존이 없어 1·2팀을 기다리지 않아도 되는 유일한 묶음이다.
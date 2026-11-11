# 도메인 지도

14개 도메인 · 18개 테이블(`files` 포함, `global/file`은 도메인 아님. `setlists`는 2026-10-30 추가, 이슈 #85). 엔드포인트 총계는 파일 삭제 신규 확인(2026-09-11, `docs/specs/api-spec.md` §10 gap #10)으로 표기가 갱신 중이다 — 정확한 총계는 `docs/specs/api-spec.md` "알려진 공백" 참고.

> **버전**: 전 도메인 `/v1/`, 예외 없음. `rehearsal`의 `/v0/` 예외는 초안이 정리돼 2026-10-05에 해소했다(`docs/specs/api-spec.md` §3).

## 전체

| 도메인 | 담당 | 소유 테이블 | 개수 |
|---|---|---|---|
| `auth` | 1팀 | (없음 · `users` 읽기·가입 시 생성) | 4 |
| `user` | 1팀 | `users` `user_instruments` | 9 |
| `team` | 2팀 | `teams` `team_genres` `team_members` | 10 |
| `rehearsal` | 2팀 | `rehearsals` | 6 |
| `recruit` | 3팀 | `recruit_posts` | 5 |
| `join` | 3팀 | `join_requests` | 12 |
| `notification` | 3팀 | `notifications` | 2 |
| `bookmark` | 3팀 | `bookmarks` | 3 |
| `group` | 4팀 | `groups_` `group_members` | 8 |
| `notice` | 4팀 | `group_notices` | 5 |
| `performance` | 4팀 | `performances` | 10 |
| `song` | 4팀 | `songs` | 2 |
| `setlist` | 4팀 | `team_songs` `setlists` | 2 |
| `dashboard` | 3팀 | (없음) | 1 |

> **엔드포인트 상세 명세(요청·응답 구조)는 `docs/specs/`에 있다** (`api-spec.md` · `dto-spec.md`). 정본은 노션 `API 명세서 v0`, `docs/specs/`는 대조 날짜가 명시된 스냅샷이다.
> 명세에 없으면 **임의로 만들지 말고 요청할 것.** 응답 필드나 에러 코드를 추측해서 채우지 않는다.
> DTO 이름은 `docs/conventions/dto-naming.md`, 컬럼은 `docs/conventions/erd.md`에 있다.

`global/file`은 도메인이 아니고 엔드포인트 2개(업로드 URL 발급 · 파일 삭제)를 갖는다. **1팀 담당** — Security와 같은 인프라 성격이고, 1팀(프로필)·4팀(커버·포스터)이 함께 쓴다. 파일 삭제는 `API 초안`에서 2026-09-11에 새로 확인됐고, 2026-11-11 `DELETE /v1/files/{fileId}`에서 `DELETE /v1/files?fileUrl=`로 바꿨다(이슈 #164 — 저장한 뒤에는 프론트가 `fileId`를 알 수 없다).

## 헷갈리는 경계

**`join`은 컨트롤러가 2개다.** `JoinRequestController`(`/v1/join-requests`)와 `InvitationController`(`/v1/invitations`). URI와 컨트롤러는 나뉘지만 **엔티티는 `JoinRequest` 하나, 테이블도 `join_requests` 하나**다. `direction`(`APPLY` / `INVITE`)으로 구분한다.

- 별도 `Invitation` 엔티티나 `invitations` 테이블을 **만들지 않는다**
- 조회 시 `direction` 조건을 항상 포함한다. id 시퀀스를 공유하므로 빠뜨리면 신청 id로 초대 API를 호출하는 게 통과한다
- **단, 중복 검사에서만 `direction`을 뺀다.** 신청과 초대가 동시에 있으면 가입 경로가 둘 생기므로 양쪽을 같이 봐야 한다

**`notice`는 `group`과 분리한다.** 테이블이 `group_notices`이고 엔티티는 `GroupNotice`. `Post`라는 이름을 쓰지 않는다. 경로는 생성·목록만 `/v1/groups/{id}/notices`이고 상세·수정·삭제는 `/v1/notices/{id}`다. `API 초안`은 수정·삭제도 그룹 하위 경로로 적었지만, 이 규칙을 유지하기로 하고 노션도 함께 수정했다(2026-09-11).

**공지 목록과 상세는 별도 엔드포인트다.** 원래 명세는 목록 조회 하나로 묶여 있었지만(목록 응답에 `body`까지 포함), 팀 결정으로 `GET /v1/notices/{id}` 상세 조회를 분리 추가했다(`docs/specs/api-spec.md` §7, `docs/conventions/dto-naming.md` §8 참고). `API 초안`에도 이 엔드포인트는 없지만 팀 결정을 그대로 유지한다. 목록은 `NoticeSummaryResponse`(`body` 제외), 상세는 `NoticeDetailResponse`(`body` 포함)를 쓴다.

**팀 선택곡과 공연 셋리스트는 다른 테이블이다(2026-10-30, 이슈 #85).** `team_songs`는 팀(독립 팀 포함)이 고른 곡, `setlists`는 공연 팀이 그중에서 넘겨 모임장이 확정하는 곡이다. **팀 쪽에서는 "셋리스트"라는 말을 쓰지 않는다** — 팀은 "선택곡", 모임·공연은 "셋리스트"다. 흐름: 팀 선택곡 저장(팀장) → 셋리스트로 넘기기(공연 팀 팀장) → 모임장이 수정 후 확정 → 팀 페이지는 확정곡을 셋리스트에서 조회.

- **테이블은 둘 다 `setlist` 도메인 소유**다. `song`은 곡 마스터(`songs`)만 갖는다. `performance`도 `team`도 두 테이블을 직접 건드리지 않고 `SetlistService`를 부른다
- **엔드포인트는 나눠 갖는다.** 팀 선택곡 저장(`PUT /v1/teams/{teamId}/songs`)은 `setlist`, 공연 단위 셋리스트 4개(조회 `GET`·넘기기 `PUT .../setlist/teams/{teamId}`·확정 `PUT /v1/performances/{performanceId}/setlist`·확정 해제 `PATCH .../setlist/unconfirm`)는 `performance`가 받는다. 확정 시각(`performances.setlist_confirmed_at`)이 공연 컬럼이라 공연 존재·확정 여부·모임장을 `PerformanceService`가 확인하고, 행 처리는 `SetlistService`에 맡긴다. 반대로 하면 `PerformanceService`(상세의 셋리스트) ↔ `SetlistService`(확정 시각)가 서로 불러 순환한다(`architecture.md` §3)
- **공연 내 같은 곡 중복은 `setlists`의 `UNIQUE(performance_id, song_id)`가 막는다.** 팀 선택곡 단계에서는 막지 않고 "다른 팀이 이미 골랐어요" 표시만 한다
- DTO 이름은 `SetlistResponse`·`SetlistSubmitRequest`·`SetlistConfirmRequest`(공연 쪽), `TeamSongsSaveRequest`·`TeamSongsResponse`(팀 쪽)다(`dto-naming.md` §10)

**`user/me` 경로는 user 도메인이 아니다.** `/v1/users/me/rehearsals`는 `rehearsal`, `/v1/users/me/join-requests`는 `join`, `/v1/users/me/songs/recommendations`는 `song` 소유다. "내 것만 필터링한 뷰"일 뿐이다. `/v1/users/me/teams`(내 팀 목록)는 `team` 소유다.

**공연 참가 단위는 팀이다.** 공연자 개별 관리 API는 없다. 공연에서 팀을 빼는 것 = 팀 해체.

## 공용 enum

`global/common/enums`에만 정의한다. 여러 도메인이 쓰기 때문이다.

| enum | 쓰이는 곳 |
|---|---|
| `Instrument` | `user_instruments` `team_members` `join_requests` `recruit_posts` |
| `Level` | `user_instruments` `songs.difficulty` |
| `Genre` | `users` `teams` `songs` |
| `Region` | `users` `teams` `groups_` `recruit_posts` |
| `TargetType` | `recruit_posts` `join_requests` (`TEAM` `GROUP`) |
| `JoinStatus` | `join_requests.status` — join이 소유하고 recruit 공고 상세(`myJoinRequest.status`)도 내보낸다(PR #134) |
| `NotificationType` `NotificationTargetType` | `notifications`와 알림 발생 이벤트(`global/event/NotificationEvent`) — 2·3·4팀이 이벤트에 담아 발행한다 |

**값이 다르면 공유하지 않는다.**

- `MemberStatus`(팀) = `ACTIVE` `LEFT` `REMOVED`
- `GroupMemberStatus`(모임) = `ACTIVE` `LEFT` `BANNED`
- `BookmarkTargetType` = `TEAM` `USER` `SONG` — `TargetType`과 다르다
- `NotificationTargetType` — `TargetType`보다 값이 많다

이름이 비슷해서 재사용하고 싶어지는 지점이다. 공유하면 잘못된 값이 검증을 통과한다.

## 담당

| 팀 | 도메인 | 엔드포인트 |
|---|---|---|
| **1팀** | `auth` `user` `file` | 14 |
| **2팀** | `team` `rehearsal` | 16 |
| **3팀** | `recruit` `join` `notification` `bookmark` `dashboard` | 23 |
| **4팀** | `group` `notice` `performance` `song` `setlist` | 27 |

> 팀 곡 추천(AI)은 song·setlist 중 소유가 정해지지 않아(`docs/specs/api-spec.md` 공백 #5) 위 개수에 넣지 않았다 — `docs/conventions/dto-naming.md` §10(5개)과 합계가 1 다른 이유다. setlist 2개는 팀 선택곡 저장·조회(조회는 2026-11-02, 이슈 #103 신규).
> 곡 수동 동기화(`POST /v1/songs/sync`, 2026-11-11, 이슈 #162)는 노션 `API 초안`에 없는 운영용 API라 위 개수에 넣지 않았다. 로그인한 사용자면 누구나 부를 수 있다(`api-spec.md` song).

`dashboard`는 2·3·4팀의 Service를 모두 호출하므로 3팀 작업 중 **마지막**에 만든다.

## 팀 간 의존

혼자 끝낼 수 없는 지점이다. 착수 전에 인터페이스를 합의한다.

| 지점 | 누가 → 누구 | 내용 |
|---|---|---|
| `global/common` `enums` `exception` | **1팀 → 전원** | 나머지 셋이 이것 없이는 시작 못 한다. **최우선** |
| Security · `@AuthUser` | **1팀 → 전원** | 주입 타입(`Long userId`)을 먼저 확정 |
| `file` presigned URL | 1팀 → 4팀 | 모임 커버·공연 포스터가 이 API를 기다린다 |
| 신청·초대 승인 | 3팀 → 2팀 `TeamService` · 4팀 `GroupService` | 승인 시 멤버 추가(`TeamService.addMember`). 3팀이 직접 `team_members`를 만들지 않는다 |
| 공연 내 팀 생성 | 4팀 → 2팀 `TeamService` | `POST /v1/performances/{performanceId}/teams`가 팀을 만든다(`TeamService.createForPerformance`) |
| 공연 종료·취소 | 4팀 → 2팀 `TeamService` | 상태가 `DONE`·`CANCELED`면 소속 팀 해체(`TeamService.disbandAllByPerformance`). 셋리스트·선택곡은 남긴다 |
| 팀 요약 조회 | 3팀 bookmark · 1팀 activity → 2팀 `TeamService` | `TeamService.getSummaries` — 해체된 팀은 결과에서 빠진다 |
| 내 활동의 팀 | 1팀 activity → 2팀 `TeamService` | `TeamService.findMemberships` — 내가 속한 활동 중인 팀의 요약과 내 역할·악기 |
| 내 활동의 모임 | 1팀 activity → 4팀 `GroupMemberService` | `findMyGroupSummaries` → `List<GroupSummaryResponse>` — 내가 ACTIVE 모임원인 해체되지 않은 모임 전체와 내 역할. 가입 최신순, 페이징 없음. 화면용 `GroupService.getMyGroups`와 달리 다른 도메인이 부르는 조회라 `GroupMemberService`에 둔다(#95) |
| 내 활동의 공연 | 1팀 activity → 4팀 `PerformanceService` | `findActivitiesByUserId` → `List<PerformanceActivityResponse>` — 활동 중인 내 공연 팀이 나가는 `PLANNED`·`RECRUITING` 공연을 공연일 가까운 순으로, 한 공연에 내 팀이 여럿이면 팀마다 한 줄. 해체된 모임의 공연은 빠진다. 2팀 `findMyPerformanceTeams`와 `GroupMemberService.getNames`로 만든다(#95, PR #84 답글) |
| 모임 탈퇴·강퇴 시 공연 팀에서 빼기 | 4팀 `GroupService` → 2팀 `TeamService.leavePerformanceTeams` | 그 모임 공연들의 활동 중인 팀에서 이 사람을 `LEFT`로 뺀다. 팀장이면 `409 LEADER_MUST_DELEGATE_FIRST`로 전체를 막는다(#96). 모임 탈퇴 흐름을 `GroupService`가 묶어서 서비스끼리 순환하지 않는다 |
| 회원 탈퇴 시 팀에서 빼기 | 1팀 `UserWithdrawService` → 2팀 `TeamService.leaveAllTeams` | 속한 활동 중인 모든 팀의 팀원 행을 `LEFT`로 뺀다. 팀장 차단(`findLedTeams`)을 통과한 뒤에 부른다(#96, #115 댓글) |
| 회원 탈퇴 차단 | 1팀 `UserWithdrawService` → 2팀 `TeamService` | `TeamService.findLedTeams` — 내가 팀장인 활동 중인 팀 요약. 비어 있어야 탈퇴할 수 있다 |
| 회원 탈퇴 시 신청·초대 닫기 | 1팀 `UserWithdrawService` → 3팀 `JoinRequestService` | `cancelPendingOf` — 탈퇴자의 대기 중 신청은 `CANCELED`, 받은 초대는 `REJECTED`로 닫는다(알림 없음). 차단 통과 뒤 팀·모임 LEFT 처리 앞에서 부른다(이슈 #136) |
| 공연 상세의 팀 정보 | 4팀 → 2팀 `TeamService` | `getActiveSummaries`(공연의 활성 팀 요약) · `findMyTeamIds`(내가 그 공연에서 속한 팀 id 목록) · `getSummariesIncludingDisbanded`(해체된 팀 포함 요약) · `findMyPerformanceTeams`(내 공연 팀, 해체된 팀 포함) |
| 팀 개별 해체 | 2팀 `TeamService` → 4팀 `SetlistService.deleteByTeam` | 개별 해체 때만 그 팀의 `setlists`·`team_songs` 행을 지운다 — 다른 팀이 그 곡을 넘기거나 "이미 골랐어요" 표시 없이 고를 수 있게. 공연 종료의 일괄 해체에서는 부르지 않는다. 연결 완료(2026-11-04, 이슈 #130, `docs/conventions/architecture.md` §6) |
| 팀 상세의 선택곡·공연 정보 | 2팀 `TeamService` → 4팀 `SetlistService.findTeamSongs` · `PerformanceMemberService.getBrief` | 연결 완료(2026-11-04, 이슈 #130). 응답은 `setlist` 대신 `songs`와 `performance`를 채운다 |
| 셋리스트의 팀 확인 | 4팀 setlist → 2팀 `TeamMemberService` | `ensureLeader` · `ensureActiveMember` · `getPerformanceId` · `getNames`(해체된 팀 포함). 다른 Service를 부르지 않는 조회 전용이라 순환이 없다(`docs/conventions/dto-naming.md` §3) |
| 가입·초대 승인 | 3팀 join → 2팀 `TeamService.addMember` · 4팀 `GroupMemberService.addMember` | 승인·수락하면 팀원/모임원으로 넣는다. 이미 소속이면 join이 먼저 `ALREADY_MEMBER`로 막고, 팀장/모임장 판별·소속 확인·알림 수신자는 `TeamMemberService`(`ensureLeader` `isLeader` `isActiveMember` `getLeaderId`)·`GroupMemberService`(`ensureOwner` `isOwner` `isActiveMember` `getOwnerId`)로 묻는다 |
| 가입 신청·초대 직렬화 | 3팀 join → 2팀 `TeamService.lockTeam` · 4팀 `GroupMemberService.lockActiveGroup` | 신청·초대를 만들 때와 승인·수락할 때 트랜잭션의 첫 일반 조회 전에 대상 팀·모임 행을 잠근다. 대기 건 중복 확인과 저장 사이, 소속 확인과 `addMember` 사이에 다른 요청이 끼어들지 못하게 한다(유니크 제약 없이 같은 사람의 대기 건이 둘 생기는 것을 막는다). 승인 쪽 잠금 순서는 신청 행 → 대상 행(PR #134) |
| 공고 ↔ 신청 | 3팀 join → 3팀 recruit → 3팀 join | `JoinRequestService`가 신청을 받을 때 `RecruitPostService.getOpenPostForApply`로 공고를 확인하고, `RecruitPostService`가 공고 상세의 `appliedCount`·`myJoinRequest`를 채울 때 **조회 전용 `JoinRequestStatService`**를 부른다. `JoinRequestService`와 `RecruitPostService`가 서로 부르면 순환이라 신청 현황 조회만 다른 Service를 부르지 않는 말단 클래스로 떼었다(`architecture.md` §3). 여기에 다른 Service 호출을 넣지 않는다 |
| 공연 팀원 확인 | 2팀 `TeamService.addMember` · 3팀 가입 신청·초대 → 4팀 `PerformanceMemberService.ensureGroupMember` | 공연 팀에는 그 공연을 연 모임의 활동 모임원만 들어올 수 있다. 아니면 `403 NOT_PERFORMANCE_GROUP_MEMBER`(#96·#105). `PerformanceMemberService`는 `GroupMemberService`만 불러 순환이 없다 |
| 곡 마스터 채우기 | 4팀 `SongService.syncFromAi` → AI 서버 | 백엔드가 AI 서버 곡 카탈로그(`GET {AI}/v1/songs?cursor=&limit=`, 잠정)를 페이지로 받아 `externalId` 기준으로 upsert한다. 지우지 않는다. AI 서버는 백엔드를 부르지 않는다 — 매일 04:00 스케줄(`AI_SONG_SYNC_ENABLED`). 곡은 AI가 분석한 곡으로만 추천하므로 우리가 따로 넣지 않는다(2026-11-06, 이슈 #147, `docs/specs/api-spec.md` §12) |
| 알림 발생 | 2·3·4팀 → 3팀 `notification` | 이벤트로만 전달. `NotificationService` 직접 호출 금지. `global/event/NotificationEvent`를 **트랜잭션 안에서** 발행한다(밖에서 발행하면 버려진다). 사용법은 `docs/specs/api-spec.md` §10 |
| 회원 탈퇴 | 1팀 `UserWithdrawService` → 4팀 `GroupMemberService` | `findOwnedGroups`(내가 모임장인 해체되지 않은 모임 — 있으면 1팀이 탈퇴를 막고 모임 이름을 메시지에 쓴다) · `leaveAllGroups`(내 ACTIVE 모임원 행을 모두 `LEFT`, 활동 중 모임의 모임장이면 `OWNER_CANNOT_LEAVE`). 모임 행을 잠그고 모임원 행을 잠금 조회로 다시 읽어 탈퇴 직전 위임과 겹쳐도 모임장 없는 모임이 생기지 않는다(2026-11-03, #112) |
| 대시보드 | 3팀 → 2·4팀 | 각 도메인 Service 호출 |

**2팀의 `TeamService`가 3팀·4팀 양쪽에서 호출된다.** 팀 생성·해체·멤버 추가 메서드 시그니처는 2팀이 먼저 정해 구현했다(시그니처 표는 `docs/conventions/dto-naming.md` §3). 다른 팀이 가져다 쓰고, 맞지 않으면 요청받아 고친다.

**`PUT /v1/teams/{teamId}/songs`(팀 선택곡 저장)는 경로가 `/teams`로 시작하지만 4팀 소유다.** `team_songs`를 다루므로 `setlist` 도메인이고, 2팀의 `TeamController`에 넣지 않는다.

**AI 추천 3개는 2팀·4팀에 나뉘어 있다** — 팀원 추천(2팀), 개인 추천곡·팀 곡 추천(4팀). 룰 기반이냐 외부 모델이냐를 **두 팀이 함께 결정해야 한다.** 한쪽만 폴링 방식이 되면 프론트가 두 패턴을 구현하게 된다.

팀원 추천은 2026-11-08에 구현했다(이슈 #150): 백엔드가 후보를 골라 AI 서버(`POST {AI}/v1/recommendations/members`, 잠정)에 요청마다 동기 호출한다. 후보 조회는 1팀 `UserService.findRecommendationCandidates`를 쓴다. 곡 추천 2개는 아직 방식이 정해지지 않았다.

## 착수 순서

1. 1팀 — `global/common/dto` `enums` `exception`
2. 1팀 — Security · JWT · `@AuthUser`
3. 1팀 — `file` (4팀 포스터·커버가 기다린다)
4. 2팀 — `TeamService` 시그니처 확정 후 공유
5. 2·3·4팀 병렬
6. 3팀 — `dashboard` (다른 도메인이 끝난 뒤)

3팀은 `recruit`(5개)부터 시작하는 게 좋다. 다른 도메인 의존이 없어 1·2팀을 기다리지 않아도 되는 유일한 묶음이다.
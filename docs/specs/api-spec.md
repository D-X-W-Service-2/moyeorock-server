# API 명세 — 전 도메인 통합

- **원본**: Notion `API 명세서 수정안` 페이지의 `API 초안` 데이터베이스 (2026-09-11 대조 완료)
- **버전**: 전 도메인 `/v1/`. **예외 없다.** 합주(rehearsal, §3)만 초안 정리 전까지 `/v0/`를 유지했는데, 초안이 `/v1/`로 정리된 것을 확인하고 2026-10-05에 반영했다.
- **절 번호**: 이 파일과 `docs/specs/dto-spec.md`에서만 쓰는 좌표다
- **인증**: 모든 API는 유저 인증 필요. 예외(auth 4개)는 §11에 별도 표기
- **DTO 필드 정의**: `docs/specs/dto-spec.md` 참조. `API 초안`에는 DTO 정보가 없어 DTO 이름은 기존 `docs/conventions/dto-naming.md` 정의를 그대로 따른다. `*` 표시 Response는 원본 표에 비어 있으나 DTO 페이지의 이름·태그로 매칭한 것이다
- **경로 표기**: 초안은 가입 신청·공고를 단수형(`join-request` · `recruit-post`)으로 적었다. `docs/conventions/api-conventions.md` §5 "리소스는 복수형" 규칙과 어긋나지만, 초안 값을 그대로 신뢰해 반영하기로 했다(2026-09-11 팀 확인). 다른 리소스(`teams` `groups` `invitations` `bookmarks` 등)는 여전히 복수형이다 — `join-request` · `recruit-post` 2개만 예외다

---

## §1. 사용자 (user) — 1팀

| 메서드 | 경로 | 기능 | Request | Response |
|---|---|---|---|---|
| GET | `/v1/users/me` | 내&nbsp;프로필&nbsp;조회 | | `UserMeResponse` |
| PUT | `/v1/users/me` | 프로필&nbsp;설정&nbsp;/&nbsp;수정 | `UserUpdateRequest` | |
| DELETE | `/v1/users/me` | 탈퇴 | | `UserWithdrawResponse` * |
| POST | `/v1/users/me/onboarding` | 온보딩&nbsp;등록 | `OnboardingCreateRequest` | |
| PUT | `/v1/users/me/instruments` | 세션/실력&nbsp;수정 | `UserInstrumentUpdateRequest` | |
| GET | `/v1/users/me/activities` | 내&nbsp;활동 | | `UserActivityResponse` |
| GET | `/v1/users/{id}` | 타인&nbsp;프로필&nbsp;조회 | | `UserProfileResponse` |
| GET | `/v1/users/search?nickname=&page=&size=` ¶ | 사용자&nbsp;검색 | | `PageResponse<UserSummaryResponse>` |
| GET | `/v1/users/nickname/check?nickname=` | 닉네임&nbsp;중복&nbsp;확인 | | `NicknameCheckResponse` † |

> ¶ 초안에서 `page`·`size` 페이징 파라미터가 명시적으로 추가됐다(2026-09-11).
> † 원래 Notion `API 명세서 v0`에는 없던 엔드포인트였으나(`docs/conventions/dto-naming.md` §2가 먼저 정의), `API 초안`에 쿼리 파라미터까지 포함해 반영된 것을 2026-09-11 확인했다.
> `/v1/users/search`는 원본에서 팀 섹션에 있었지만 user 도메인 소유다.
> `PUT /v1/users/me/instruments`의 응답이 `UserInstrumentsResponse`다 (`docs/conventions/dto-naming.md` §2).

---

## §2. 팀 (team) — 2팀

| 메서드 | 경로 | 기능 | Request | Response |
|---|---|---|---|---|
| POST | `/v1/teams` | 팀&nbsp;생성 | `TeamCreateRequest` | `TeamDetailResponse` |
| GET | `/v1/teams` ¶ | 팀&nbsp;목록 | | `PageResponse<TeamSummaryResponse>` |
| GET | `/v1/teams/{id}` | 팀&nbsp;상세 | | `TeamDetailResponse` |
| PUT | `/v1/teams/{id}` | 팀&nbsp;정보&nbsp;수정 | `TeamUpdateRequest` | |
| PATCH | `/v1/teams/{id}/status` | 팀&nbsp;해체 | `TeamStatusUpdateRequest` | `TeamStatusResponse` * |
| GET | `/v1/teams/{id}/members` | 팀원&nbsp;목록 | | `TeamMembersResponse` |
| PATCH | `/v1/teams/{teamId}/members/{userId}` | 팀원&nbsp;역할&nbsp;변경 | `TeamMemberUpdateRequest` | `TeamMemberResponse` * |
| PATCH | `/v1/teams/{teamId}/members/{userId}/status` | 팀원&nbsp;내보내기·탈퇴 | `TeamMemberStatusUpdateRequest` | `TeamMemberStatusResponse` * |
| GET | `/v1/teams/{id}/members/recommendations?instrument=` | 팀원&nbsp;추천&nbsp;(AI) | | `TeamMemberRecommendationResponse` |
| GET | `/v1/users/me/teams` ‡ | 내&nbsp;팀&nbsp;목록 | | `PageResponse<TeamSummaryResponse>` |

> ¶ 팀 목록의 쿼리 파라미터: `region` · `genre` (선택). `status`는 받지 않는다 — 해체된 팀은 항상 목록에서 제외한다(2026-10-31 결정, 노션 팀 목록 페이지에도 "현재 사용하지 않음"으로 반영). 정렬은 최신순 고정(클라이언트 `sort` 무시), 페이지 크기는 기본 20·상한 20.
> ‡ 내 팀 목록은 노션 `API 초안`에 `[추가 필요]`로 올라온 신규 엔드포인트이며, 2팀 소유로 확정했다(2026-10-31). 경로는 `/users/me` 아래지만 소유는 team이다. 내가 ACTIVE 팀원이고 팀도 ACTIVE인 팀만 최근 합류 순으로 돌려준다.
> 팀 정보 수정은 응답 바디가 없다(`data: null`, 노션 기준).
> 팀을 개별로 해체(`PATCH /v1/teams/{id}/status`)하면 그 팀이 고른 곡(선택곡)도 함께 삭제한다. 해체된 팀이 곡을 쥐고 있으면 다른 팀이 그 곡을 고를 수 없기 때문이다(4팀 결정, 2026-10-31). 공연 종료로 인한 일괄 해체에서는 삭제하지 않는다. 삭제는 4팀 `SetlistService.deleteByTeam`을 부르는 방식이며, 이 메서드가 생긴 뒤 연결한다.
> 해체된 팀의 상세·팀원 목록은 읽기 전용으로 열려 있고, 상세의 `status`가 `DISBANDED`다(2026-11-02 결정, 4팀 요청 — 끝난 공연 페이지의 팀 카드가 연결된다). 수정·해체·팀원 변경·탈퇴는 해체된 팀에서 막힌다. 해체된 팀은 요약 조회(`getSummaries`)에서는 여전히 빠지고, 4팀용 `getSummariesIncludingDisbanded`로만 담긴다.
> 팀원 역할 변경과 내보내기·탈퇴에서 경로의 `{userId}`가 그 팀의 활동 중인 팀원이 아니면(속한 적이 없거나 이미 탈퇴·강퇴된 경우) `404 TEAM_MEMBER_NOT_FOUND`다. 모임의 `GROUP_MEMBER_NOT_FOUND`와 같은 상황이라 맞췄다(PR #86 리뷰).
> 팀원 내보내기·탈퇴는 `API 초안`에서도 `/status` 접미사가 붙은 완성형으로 확인됐다(2026-09-11) — 과거 DELETE→PATCH 전환 미완 문제는 해소됐다. 상태값은 `LEFT`(본인 탈퇴)·`REMOVED`(강퇴)이며, 노션 행 제목의 `KICKED`는 표기 오류다.
---

## §3. 합주 (rehearsal) — 2팀

2026-09-11 대조 때는 `API 초안`의 합주 행이 `v1/fee/...` · `{studentFeeId}` 등 무관한 경로였고 일부는 비어 있어 이 절만 `/v0/`를 유지했다. **2026-10-05 재대조에서 6개 전부 `/v1/`로 정리된 것을 확인해 반영했다** — 경로만 바뀌었고 기능·DTO는 그대로다. 합주 구현체는 아직 없어 코드 영향은 없다.

| 메서드 | 경로 | 기능 | Request | Response |
|---|---|---|---|---|
| GET | `/v1/teams/{teamId}/rehearsals?from=&to=` | 팀&nbsp;합주&nbsp;일정&nbsp;목록 | | `RehearsalsResponse` |
| POST | `/v1/teams/{teamId}/rehearsals` | 합주&nbsp;일정&nbsp;생성 | `RehearsalCreateRequest` | |
| GET | `/v1/rehearsals/{id}` | 합주&nbsp;일정&nbsp;상세 | | `RehearsalDetailResponse` |
| PUT | `/v1/rehearsals/{id}` | 합주&nbsp;일정&nbsp;수정 | `RehearsalUpdateRequest` | |
| DELETE | `/v1/rehearsals/{id}` | 합주&nbsp;일정&nbsp;삭제 | | |
| GET | `/v1/users/me/rehearsals?from=&to=` | 내&nbsp;합주&nbsp;일정 | | `RehearsalsResponse` |

---

## §4. 모집 공고 (recruit) — 3팀

| 메서드 | 경로 | 기능 | Request | Response |
|---|---|---|---|---|
| POST | `/v1/recruit-post` | 공고&nbsp;작성 | `RecruitPostCreateRequest` | `RecruitPostDetailResponse` |
| GET | `/v1/recruit-post` ¶ | 공고&nbsp;목록 | | `PageResponse<RecruitPostSummaryResponse>` |
| GET | `/v1/recruit-post/{id}` | 공고&nbsp;상세 | | `RecruitPostDetailResponse` |
| PUT | `/v1/recruit-post/{id}` | 공고&nbsp;수정 | `RecruitPostUpdateRequest` | `RecruitPostDetailResponse` |
| PATCH | `/v1/recruit-post/{id}/status` | 공고&nbsp;마감·삭제 | `RecruitPostStatusUpdateRequest` | `RecruitPostStatusResponse` * |

> ¶ 공고 목록의 쿼리 파라미터: `targetType` · `region` · `instrument` · `status` · `page` · `size` (전부 선택). `authorId`는 Notion `API 초안`에 없어 뺐다(2026-09-25 대조). 정렬 파라미터는 없고 최신순 고정이다.
> Actor(Notion `API 초안`): 작성·수정·마감은 `모임장 / 팀장`, 목록·상세는 공통. 팀장·모임장 검증은 2팀 `TeamService`·4팀 `GroupService`가 머지된 뒤 붙이고, 그 전까지 구현은 "수정·마감은 작성자 본인만, 작성은 로그인만"이다.
> 마감(`CLOSED`)된 공고는 수정(`PUT`)할 수 없다 — 작성자라도 `409 INVALID_STATE`다(권한 검사가 먼저라 작성자가 아니면 `403 NOT_POST_AUTHOR`). 명세에는 없는 서버 정책으로, 마감 뒤 모집 세션·인원이 바뀌어 이미 들어온 신청과 어긋나는 것을 막는다 — Notion 반영 필요.
> `wantedSlots`에 같은 `instrument`가 두 번 오면 `400 VALIDATION_FAILED`다(예: BASS 1명 + BASS 2명). 명세에는 없는 서버 검증으로, 세션별 집계·마감 기준이 모호해지는 것을 막는다 — Notion 반영 필요.
> 경로가 단수형 `recruit-post`다 — 상단 "경로 표기" 참고.
> ⚠️ DTO 이름(`RecruitPostStatusUpdateRequest` 등)은 `API 초안`에 없어 기존 `docs/conventions/dto-naming.md` 정의를 그대로 썼다. 과거 노션 `API 명세서 v0`이 마감·삭제 Request를 `RecruitPostStatusResponse`로 오기했던 문제는 이 표와 무관하다.

---

## §5. 가입 신청 (join · JoinRequestController) — 3팀

팀 가입, 모임 가입, 팀 제안 **전부 이걸로 처리**한다. 초대(§6)와 엔티티·테이블(`join_requests`)을 공유하고 `direction=APPLY`로 구분한다.

| 메서드 | 경로 | 기능 | Request | Response | 비고 |
|---|---|---|---|---|---|
| POST | `/v1/join-request` | 가입&nbsp;신청 | `JoinRequestCreateRequest` | | |
| GET | `/v1/join-request` ¶ | 받은&nbsp;신청&nbsp;목록 | | `PageResponse<JoinRequestResponse>` | 모임장/팀장 |
| GET | `/v1/users/me/join-request?status=` | 내&nbsp;신청&nbsp;목록 | | `PageResponse<JoinRequestResponse>` | |
| PATCH | `/v1/join-request/{id}/approve` | 신청&nbsp;승인 | | `JoinApprovedResponse` * | 모임장/팀장 |
| PATCH | `/v1/join-request/{id}/reject` | 신청&nbsp;거절 | | `JoinDecisionResponse` * | 모임장/팀장 |
| PATCH | `/v1/join-request/{id}/cancel` | 신청&nbsp;취소 | | `JoinDecisionResponse` * | 신청자 본인 |

> ¶ 받은 신청 목록의 쿼리 파라미터: `targetType` · `targetId` · `status` (전부 선택).
> 경로가 단수형 `join-request`다 — 상단 "경로 표기" 참고. `join_requests` 테이블명은 그대로다.

---

## §6. 초대 (join · InvitationController) — 3팀

`direction=INVITE`. 별도 엔티티·테이블을 만들지 않는다 (`docs/conventions/domains.md` 참조).

| 메서드 | 경로 | 기능 | Request | Response |
|---|---|---|---|---|
| POST | `/v1/invitations` | 초대&nbsp;보내기 | `InvitationCreateRequest` | |
| GET | `/v1/invitations` ¶ | 보낸&nbsp;초대&nbsp;목록 | | `PageResponse<InvitationResponse>` |
| GET | `/v1/users/me/invitations?status=` | 받은&nbsp;초대&nbsp;목록 | | `PageResponse<InvitationResponse>` |
| PATCH | `/v1/invitations/{id}/accept` | 초대&nbsp;수락 | | `JoinApprovedResponse` * |
| PATCH | `/v1/invitations/{id}/decline` | 초대&nbsp;거절 | | `JoinDecisionResponse` * |
| PATCH | `/v1/invitations/{id}/cancel` | 초대&nbsp;취소 | | `JoinDecisionResponse` * |

> ¶ 보낸 초대 목록의 쿼리 파라미터: `targetType` · `targetId` · `status` (전부 선택).

---

## §7. 모임 (group) · 공지 (notice) — 4팀

### group

| 메서드 | 경로 | 기능 | Request | Response | 비고 |
|---|---|---|---|---|---|
| POST | `/v1/groups` | 모임&nbsp;생성 | `GroupCreateRequest` | | |
| GET | `/v1/groups/my?keyword=&page=&size=` ※ | 내&nbsp;모임&nbsp;목록&nbsp;(검색) | | `PageResponse<GroupSummaryResponse>` | |
| GET | `/v1/groups/{id}` | 모임&nbsp;상세&nbsp;(동아리&nbsp;홈) | | `GroupDetailResponse` | |
| PUT | `/v1/groups/{id}` | 모임&nbsp;정보&nbsp;수정 | `GroupUpdateRequest` | | |
| PATCH | `/v1/groups/{groupId}/status` | 모임&nbsp;해체 ※ | — | — (`data: null`) | 모임장 |
| GET | `/v1/groups/{id}/members?keyword=&page=&size=` ¶ | 모임원&nbsp;목록 | | `PageResponse<>` (구 `GroupMembersResponse`) § | |
| PATCH | `/v1/groups/{groupId}/members/{userId}` | 모임원&nbsp;역할&nbsp;변경&nbsp;(모임장&nbsp;위임) | `GroupMemberUpdateRequest` | | 모임장 |
| PATCH | `/v1/groups/{groupId}/members/{userId}/status` | 모임원&nbsp;내보내기·탈퇴 | — | — (`data: null`) * | |

> `API 초안`에서도 원래 역할 변경과 같은 경로(`/members/{userId}`)를 공유하고 있었으나, `docs/conventions/api-conventions.md` §5 규칙(상태 전이는 `/status`)에 맞춰 내보내기·탈퇴 경로에 `/status`를 붙이고 노션도 함께 수정했다(2026-09-11). 과거 "DELETE → PATCH 전환 미완" 문제는 해소됐다.
> \* **2026-10-05 대조**: 내보내기·탈퇴는 `API 초안`(2026-09-23) 기준으로 **요청·응답 바디가 모두 없다**. 이 문서의 이전 스냅샷(2026-09-14)에 있던 `GroupMemberStatusUpdateRequest`·`GroupMemberStatusResponse`를 걷었다 — 더 최신 문서를 따른 것이다. 상태값은 서버가 판단한다(요청자 == 대상 → `LEFT`, 모임장이 타인 → `BANNED`).
> 모임원 목록 항목(`GroupMemberResponse`)도 같은 날 `API 초안` 기준으로 평탄화 구조로 교체했다(`docs/specs/dto-spec.md` §7).
> § 원본 DTO 페이지 `GroupMembersResponse`가 `PageResponse<>`로 개명된 이력이 있다(2026-08-22). 완성형은 `PageResponse<GroupMemberResponse>` (`docs/conventions/dto-naming.md` §8). 필드는 `docs/specs/dto-spec.md` §7 참조. `API 초안`에는 DTO 정보가 없어 이 이력은 갱신되지 않았다.
> ¶ 모임원 목록의 쿼리 파라미터: `keyword`(닉네임 검색) · `page` · `size` (전부 선택) — `API 초안`에서 확인됐으나 이번 반영(2026-09-11) 당시 누락됐던 것을 2026-09-30 대조로 뒤늦게 확인해 추가했다.
> ※ **2026-10-29 결정(이슈 #80)** — `API 초안`의 `[추가 필요]` 3행을 이렇게 정리하고 노션도 함께 고쳤다.
> - **전체 모임 검색·목록(`GET /v1/groups?keyword=&region=`)은 만들지 않는다.** 모임은 전체 목록을 열지 않고 내가 속한 모임만 보여준다. 모임에 들어가는 길은 초대·공고다
> - **내 모임 목록은 `GET /v1/groups/my`** — 초안의 `/v1/users/me/groups`에서 옮겼다(다른 "내 ~ 목록"은 `/v1/users/me/...` 패턴이라 이 경로만 예외다). 검색은 여기서만 한다: `keyword`(모임 이름 부분 일치, 앞뒤 공백 무시, `%`·`_`는 글자 그대로) · `page` · `size`(기본·최대 20, 넘으면 20으로 자른다), 전부 선택. 정렬은 고정이라 `sort`는 받지 않는다. 내가 ACTIVE 모임원인, 해체되지 않은 모임을 **가입 최신순**으로 준다
> - **모임 해체는 soft delete다** — 요청 바디 없이 `groups_.status`를 `DISBANDED`로 바꾼다. 모임장만 할 수 있고, 다른 모임원이 남아 있어도 된다. 해체된 모임은 **모임·모임원·공지 엔드포인트 전부에서 404 `GROUP_NOT_FOUND`**(없는 모임)이고 내 모임 목록에서 빠진다. 모임원·공지 행은 지우지 않는다(`architecture.md` §6). 대상이 이 모임인 **모집 중 공고(`OPEN`)는 같은 트랜잭션에서 마감(`CLOSED`)한다** — 공고 목록은 모임을 거치지 않아 해체 여부로 걸러 줄 곳이 없기 때문이다(PR #67 리뷰 반영, 공고 행도 지우지 않는다)

### notice

엔티티는 `GroupNotice`, 테이블은 `group_notices`. `Post`라는 이름을 쓰지 않는다.

| 메서드 | 경로 | 기능 | Request | Response | 비고 |
|---|---|---|---|---|---|
| POST | `/v1/groups/{id}/notices` | 공지&nbsp;작성 | `NoticeCreateRequest` | | 모임장 |
| GET | `/v1/groups/{id}/notices?isPinned=&page=&size=` ¶ | 공지&nbsp;목록 | | `PageResponse<NoticeSummaryResponse>` | 고정글(`is_pinned`) 우선 정렬 * |
| GET | `/v1/notices/{id}` | 공지&nbsp;상세 | | `NoticeDetailResponse` | ‖ |
| PUT | `/v1/notices/{id}` | 공지&nbsp;수정 | `NoticeUpdateRequest` | | |
| DELETE | `/v1/notices/{id}` | 공지&nbsp;삭제 | | | |

> \* "고정글 우선 정렬" · "is_pinned 토글" 메모는 원본에서 알림·북마크 행에 붙어 있었으나 문맥상 공지 기능으로 판단해 옮겼다. 2026-10-07 대조에서 `API 초안`의 공지 목록 Query Parameter 표가 `isPinned`를 "고정 공지만 조회할지 여부"로 적고 있는 것을 확인했으므로 **목록 필터로 확정**한다(별도 토글 엔드포인트가 아니다). 고정 여부를 바꾸는 것은 공지 수정(`PUT /v1/notices/{id}`)의 `isPinned` 필드다.
> ‖ `API 초안`에도 이 엔드포인트는 없다. **팀 결정으로 목록·상세를 분리해 신규 추가한 엔드포인트**로, 기존 문서의 결정을 그대로 유지한다 — 목록은 `body`를 뺀 `NoticeSummaryResponse`, 상세는 전체 필드를 담는 `NoticeDetailResponse`다(`docs/specs/dto-spec.md` §7).
> `API 초안`은 공지 수정·삭제를 `/groups/{id}/notices/{id}`(그룹 하위 경로)로 적었으나, 기존 컨벤션(단독 경로 `/notices/{id}`)을 유지하기로 하고 노션도 함께 수정했다(2026-09-11). 생성·목록은 그대로 `/groups/{id}/notices` 아래에 둔다.
> **2026-10-05 결정** — ① 공지 상세는 `API 초안`에 없던 엔드포인트였는데 이번에 노션에도 페이지를 만들어 양쪽을 맞췄다. ② **공지 응답에 작성자(`author`)를 담지 않는다** — 작성·수정·삭제 권한이 현재 모임장으로 고정이라 구분할 이유가 없다. ③ **공지 권한은 "현재 모임장"이다** — 작성자가 전 모임장이어도 현 모임장만 수정·삭제한다.
> ¶ 공지 목록의 쿼리 파라미터: `isPinned` · `page` · `size` (전부 선택). **노션 `API 초안`(2026-09-23)에 이미 있던 것이 이 문서의 이전 스냅샷(2026-09-14)에 반영되지 않았던 항목**으로, 2026-10-07 대조에서 가져왔다 — 노션 쪽은 고칠 것이 없었다. `isPinned`를 생략하면 고정글 우선 정렬로 전체를, `true`/`false`면 해당 공지만 최신순으로 내려준다.

---

## §8. 공연 (performance) — 4팀

| 메서드 | 경로 | 기능 | Request | Response | 비고 |
|---|---|---|---|---|---|
| POST | `/v1/groups/{groupId}/performances` | 공연&nbsp;생성 | `PerformanceCreateRequest` | `PerformanceDetailResponse` ※ | 모임장 |
| GET | `/v1/groups/{groupId}/performances?period=&page=&size=` ※ | 모임&nbsp;공연&nbsp;목록 | | `PageResponse<PerformanceSummaryResponse>` | 모임원 |
| GET | `/v1/performances/{performanceId}` | 공연&nbsp;상세 | | `PerformanceDetailResponse` | 모임원 |
| PUT | `/v1/performances/{performanceId}` | 공연&nbsp;정보&nbsp;수정 | `PerformanceUpdateRequest` | `PerformanceDetailResponse` | 모임장 · 7.4 공지 + 공연 포스터 |
| PATCH | `/v1/performances/{performanceId}/status` | 공연&nbsp;상태&nbsp;변경 | `PerformanceStatusUpdateRequest` | `PerformanceStatusResponse` * | 모임장 · `DONE` 시 소속 팀 해체 → 2팀 `TeamService` 호출 |
| POST | `/v1/performances/{performanceId}/teams` | 공연&nbsp;내&nbsp;팀&nbsp;생성 | `PerformanceTeamCreateRequest` | TeamDetailResponse 형태 * | 모임원 · 7.1 생성자 = OWNER · 2팀 `TeamService` 호출 |
| GET | `/v1/performances/{performanceId}/setlist` | 공연&nbsp;셋리스트&nbsp;조회 ◆ | | `SetlistResponse` | 모임원 · 확정 전엔 넘겨진 곡 전체, 확정 후엔 확정곡만 |
| PUT | `/v1/performances/{performanceId}/setlist/teams/{teamId}` | 셋리스트로&nbsp;넘기기 ◆ | `SetlistSubmitRequest` | `SetlistResponse` | 공연 팀 팀장 · 확정 후 409(해제하면 다시 가능) |
| PUT | `/v1/performances/{performanceId}/setlist` | 공연&nbsp;셋리스트&nbsp;확정 ◆ | `SetlistConfirmRequest` | `SetlistResponse` | 모임장 · 넘겨진 곡만, 요청에 없는 곡은 빠짐 |
| PATCH | `/v1/performances/{performanceId}/setlist/unconfirm` | 공연&nbsp;셋리스트&nbsp;확정&nbsp;해제 ◆ | — | `SetlistResponse` | 모임장 · 미확정·끝난·취소된 공연이면 409 |

> ◆ **2026-10-30 셋리스트 흐름 변경(이슈 #85)** — 팀 선택곡(`team_songs`)과 공연 셋리스트(`setlists`)를 테이블로 나누고(`erd.md` §15·§18), 공연 단위 셋리스트 4개(확정 해제 포함)를 **performance가 받는다**(`docs/conventions/domains.md` "헷갈리는 경계"). 확정 시각이 `performances.setlist_confirmed_at`이라 `PerformanceService`가 공연·확정 여부·모임장을 확인하고 행 처리는 `SetlistService`에 맡긴다. 조회·넘기기는 신규, 확정은 기존 행(`API 초안` 2026-10-30 수정 반영). 확정 요청 DTO 이름은 `dto-naming.md` §9의 `SetlistConfirmRequest`다(노션도 2026-11-02에 `SetlistUpdateRequest`에서 맞췄다). **확정 해제는 2026-11-02에 추가했다**(PR #87 리뷰) — 확정 뒤에는 팀이 곡을 넘길 수 없고 모임장은 넘겨진 곡 안에서만 고를 수 있어, 해제가 없으면 확정 때 뺀 곡을 다시 넣을 길이 없었다. 해제하면 확정 시각과 `sort_order`를 비우고 팀이 다시 넘길 수 있다. 노션에 페이지를 새로 만들었고, 구현은 셋리스트 API 때 한다.
> 이전 기록(2026-09-11): `API 초안`이 셋리스트 확정을 공연 도메인 행으로 적었지만 당시 소유는 setlist였다 — `team_songs` 하나에 팀 선택과 확정이 섞여 있어 한 서비스가 `UNIQUE`를 검증해야 했기 때문이다. 테이블을 나누면서 그 이유가 사라졌다.
> `API 초안`의 이 7개 행은 반영 당시(2026-09-10 이전) `인증 여부`·`진행 상태`가 비어 있었다. 모든 API는 인증이 필요하다는 원칙(상단 참고)에 따라 2026-09-11에 전부 "인증 필요"로 채우고 노션도 함께 수정했다.
> ※ **2026-10-30 대조(이슈 #83)** — `API 초안`(2026-10-08 수정)과 대조해 경로 변수를 `{performanceId}`로, 비고에 노션 Actor를 맞췄다. 어긋나는 곳은 이렇게 정했다.
> - **생성 응답은 `PerformanceDetailResponse`** — 노션 생성 응답(`id`·`title`·`description`·`performedAt`·`venue`·`posterImage`·`createdAt`)을 모두 포함하는 상세 응답을 쓴다(`docs/conventions/dto-naming.md` 규칙 3 "생성 응답은 상세 재사용")
> - **`venue`는 필수** — 노션 생성·수정 요청의 Required가 O다. `erd.md`의 "7.1.1 필수 X" 설명을 정정했다(컬럼은 NULL 허용 그대로)
> - **모임 공연 목록** — 다가올·지난 구분은 쿼리 `period`로 받는다(2026-11-02, PR #84 리뷰, 공백 #13). 기준은 공연일이 아니라 **공연 상태**다 — `performances.status`는 그대로 두고 목록에서만 `PLANNED`·`RECRUITING` → `UPCOMING`, `DONE`·`CANCELED` → `PAST`로 묶는다. `period`는 선택이고 없으면 전체, 없는 값은 `400 VALIDATION_FAILED`. 응답 항목의 `status`는 실제 공연 상태(`PLANNED` 등)다. 정렬은 고정이라 `sort`는 받지 않는다 — `UPCOMING`은 공연일 가까운 순, `PAST`와 전체는 공연일 최근 순, 공연일이 같으면 id 순(같은 방향). `page`·`size`는 기본·최대 20
>   - 모임장이 `DONE`으로 바꾸기 전에는 날짜가 지난 공연도 `UPCOMING`에 남는다. 화면 안내로 푼다
> - **이슈 #83 구현 범위** — 생성·목록·상세·수정 4개. 상세의 `teams`·`myTeamIds`·`setlist`는 명세에 있지만 아직 응답에 없다 — `teams`·`myTeamIds`는 상태 변경·공연 내 팀 생성과 함께 2팀 `TeamService`(#79) 이후, `setlist`는 셋리스트 도메인 구현 때 추가한다
> - **상태 변경** — 노션의 상태 값(`PLANNED`·`ONGOING`·`ENDED`·`CANCELLED`), 응답의 `updatedAt`, 에러 코드 `NOT_GROUP_ADMIN`이 ERD·모임 코드(`NOT_GROUP_OWNER`)와 다르다. 상태 변경을 구현할 때 정한다(공백 #14)

---

## §9. 노래 (song) · 셋리스트 (setlist) — 4팀

### song

| 메서드 | 경로 | 기능 | Request | Response | 비고 |
|---|---|---|---|---|---|
| GET | `/v1/songs?keyword=&genre=&page=&size=` ¶ | 곡&nbsp;검색 | | `PageResponse<SongResponse>` | 세션별 난이도 포함 |
| GET | `/v1/users/me/songs/recommendations` | 내&nbsp;취향&nbsp;추천곡 | | `SongRecommendationResponse` | 6.2.1 세션별 실력·장르 반영 |
| GET | `/v1/teams/{teamId}/songs/recommendations` | 팀&nbsp;곡&nbsp;추천&nbsp;(AI) | | `TeamSongRecommendationResponse` | 7.2 · 팀원 |

> ¶ `API 초안`에서 `page`·`size` 페이징 파라미터가 명시적으로 추가됐다(2026-09-11).
> ⚠️ `docs/conventions/domains.md`는 song 2개로 집계한다. 팀 곡 추천을 song에 두면 3개가 되므로 **소유 도메인(song vs setlist) 확인 필요.** `API 초안`은 이 3개를 모두 "노래 / 셋리스트"로 묶어만 두어 이 논쟁을 해소하지 못한다. 2026-10-30에 이름을 "팀 셋리스트 추천"에서 바꿨다 — 팀 쪽에서는 "셋리스트"라는 말을 쓰지 않는다(이슈 #85).

### setlist

`team_songs`(팀 선택곡)·`setlists`(공연 셋리스트) 두 테이블을 소유한다(2026-10-30, 이슈 #85). 엔드포인트는 팀 선택곡 저장 하나이고, 공연 셋리스트 조회·넘기기·확정·확정 해제는 §8에서 performance가 받는다. performance·team은 두 테이블을 직접 건드리지 않고 `SetlistService`를 부른다.

| 메서드 | 경로 | 기능 | Request | Response | 비고 |
|---|---|---|---|---|---|
| PUT | `/v1/teams/{teamId}/songs` | 팀&nbsp;선택곡&nbsp;저장 | `TeamSongsSaveRequest` | `TeamSongsResponse` | 팀장 · 독립 팀 포함. 기존 선택곡 전체 대체 |

> 2026-10-30 변경: 경로 `/v1/teams/{id}/setlist` → `/v1/teams/{teamId}/songs`, 이름 "팀 셋리스트 저장" → "팀 선택곡 저장", 요청에서 `sortOrder`·`progress`를 뺐다(`API 초안` 수정 반영). 선택곡을 바꿔도 이미 넘긴 셋리스트는 바뀌지 않고, 셋리스트 확정 뒤 곡 추가는 UI에서 막는다.

---

## §10. 알림 (notification) · 북마크 (bookmark) · 대시보드 (dashboard) · 파일 (file) — 3팀

### notification

발생은 2·3·4팀 도메인에서 **이벤트로만** 전달받는다 (`NotificationService` 직접 호출 금지).

> **발행하는 쪽**: `global/event/NotificationEvent`(`userId`·`type`·`message`·`targetType`·`targetId`)를 `ApplicationEventPublisher`로 발행한다. `message`는 사용자에게 그대로 보이는 문장이라 발행하는 쪽이 만든다(255자 초과는 잘린다).
> **트랜잭션 안에서 발행해야 한다.** 수신부는 발행한 쪽이 커밋된 뒤에만 별도 트랜잭션으로 저장하므로, 롤백된 작업의 알림은 남지 않고 알림 저장이 실패해도 핵심 작업은 실패하지 않는다. 대신 알림이 유실될 수 있고(서버 로그에만 남음), 트랜잭션 밖에서 발행하면 이벤트가 버려진다.
> 읽음 처리의 `ids`는 null·빈 배열이면 전체 읽음이고, 최대 100개다(명세에 없는 서버 정책 — 초과하면 `400 VALIDATION_FAILED`). 남의 알림 id는 오류 없이 무시된다.

| 메서드 | 경로 | 기능 | Request | Response |
|---|---|---|---|---|
| GET | `/v1/notifications?page=&size=` | 알림&nbsp;목록 | | `NotificationsResponse` |
| PATCH | `/v1/notifications/read` | 알림&nbsp;읽음 | `NotificationReadRequest` | `NotificationReadResponse` * |

### bookmark

| 메서드 | 경로 | 기능 | Request | Response |
|---|---|---|---|---|
| POST | `/v1/bookmarks` | 저장&nbsp;추가 | `BookmarkCreateRequest` | `BookmarkResponse` (201) |
| GET | `/v1/bookmarks?targetType=&page=&size=` | 저장&nbsp;목록 | | `PageResponse<BookmarkResponse>` |
| DELETE | `/v1/bookmarks/{id}` | 저장&nbsp;삭제 | | `DeleteResponse` |

> **임시 구현 범위 (2026-10-26)**: 저장 대상 `TEAM`·`SONG`은 2팀 `TeamService`·4팀 `SongService`가 머지되기 전이라 **`USER`만 저장할 수 있다.** `TEAM`·`SONG` 저장 요청은 `400 VALIDATION_FAILED`다. 두 서비스가 생기면 대상 확인과 요약 조회를 잇는다(후속 이슈).
> **중복 저장은 `409 BOOKMARK_DUPLICATED`다.** 더블 탭·재전송·다른 기기에서 먼저 저장한 경우처럼 정상 흐름에서도 생기므로 프론트는 409를 "이미 저장됨"(성공)으로 취급한다. 명세에는 없는 서버 정책이다.
> 없는 사용자·탈퇴한 사용자를 저장하면 `404 USER_NOT_FOUND`다. **자기 자신은 저장할 수 없다** — `400 VALIDATION_FAILED`(명세에 없는 서버 정책. 허용하면 "저장한 유저 N명" 집계에 본인이 섞인다). 프론트는 본인 항목의 저장 버튼을 숨기길 권장한다.
> 삭제는 실제로 행이 사라지는 hard delete이고, 남의 저장·없는 저장은 똑같이 `404 BOOKMARK_NOT_FOUND`다(존재 여부를 드러내지 않음).
> **대상이 삭제·탈퇴·해체돼 조회되지 않으면 목록에서 빼지 않고 `target`만 없다.** JSON에서는 `"target": null`로 내려간다 — 프론트는 `target`이 null이면 "삭제된 항목"으로 표시한다. 목록 정렬은 명세에 없어 최신순 고정이다.
> Notion `API 초안`의 `[추가 필요] 저장 해제 (대상 기준)`(`DELETE /v1/bookmarks?targetType=&targetId=`)는 요청·응답이 비어 있어 구현하지 않았다.

### file (`global/file` — 도메인 아님)

1팀(프로필)·4팀(커버·포스터)이 쓰므로 **최우선 착수 대상.**

| 메서드 | 경로 | 기능 | Request | Response |
|---|---|---|---|---|
| POST | `/v1/files/presigned-url` | 업로드&nbsp;URL&nbsp;발급 | `PresignedUrlCreateRequest` | `PresignedUrlResponse` |
| DELETE | `/v1/files/{fileId}` | 파일&nbsp;삭제 † | | `DeleteResponse` |

**로컬 저장 전용 임시 엔드포인트** — AWS 비용 때문에 당분간 서버 로컬 디스크에 저장하기로 한 결정(이슈 #18) 때문에 생긴 것이다. S3로 전환하면 `LocalFileStorageClient`와 함께 **둘 다 제거한다.** `API 초안`에는 없다.

| 메서드 | 경로 | 기능 | Request | Response |
|---|---|---|---|---|
| PUT | `/v1/files/local-upload/**` | 업로드&nbsp;수신&nbsp;‡ | 파일 바이트(raw body) | — (`data: null`) |
| GET | `/v1/files/local/**` | 파일&nbsp;서빙&nbsp;‡ | | 원시 바이트 |

> † `API 초안`에서 새로 확인된 엔드포인트다(2026-09-11). 응답은 공용 `DeleteResponse`(`{id, deleted}`)를 쓴다.
> ‡ 로컬 저장에서는 발급된 `uploadUrl`이 S3가 아니라 이 서버의 `PUT /v1/files/local-upload/{fileKey}`를 가리킨다. 서빙(`GET /v1/files/local/**`)은 프로필·커버·포스터가 공개 정보라 인증 없이 열고(`PublicEndpoints` 등록), 원시 바이트를 그대로 내려줘야 해서 `api-conventions.md` §1의 "모든 응답은 `ApiResponse`로 감싼다" 규칙의 예외다.
> 초안에는 `PUT {uploadUrl}`(파일 업로드) 행도 있다. S3로 전환하면 그 말대로 **S3 주소에 직접 올리는 클라이언트 동작**이 되어 백엔드 엔드포인트가 아니게 된다 — 위 임시 엔드포인트가 그때 사라지는 이유다.

**업로드 PUT 규칙** — 발급받은 `uploadUrl`로 파일을 올릴 때의 규칙이다. 로컬과 S3가 **인증 헤더 하나만 다르다.**

| 항목 | 로컬 (지금) | S3 (전환 후) |
|---|---|---|
| `Authorization` 헤더 | **필수** (`Bearer {token}`). 업로더 본인만 올릴 수 있다 | **넣으면 안 된다.** presigned URL 서명과 충돌해 S3가 거부한다 |
| `Content-Type` 헤더 | 발급 때 보낸 `contentType`과 같아야 한다(대소문자 무시). 다르거나 없으면 415 `UNSUPPORTED_MEDIA_TYPE` | 같아야 한다. 다르면 S3가 403 |
| 바디 | 파일 바이트 그대로(raw). `FormData`(multipart)로 보내면 Content-Type이 달라 415. 비어 있으면 400 `EMPTY_FILE`, 10MB 초과면 400 `FILE_SIZE_EXCEEDED` | 파일 바이트 그대로(raw) |
| 만료 | `expiresAt`(발급 후 10분)이 지나면 403 `UPLOAD_URL_EXPIRED` | 만료되면 S3가 403 |
| 재업로드 | 만료 전엔 같은 `uploadUrl`로 다시 올리면 덮어쓴다 | 같음 |

> ⚠️ **S3 전환 예정(조만간).** 전환해도 `PresignedUrlResponse` 구조(`fileId`·`uploadUrl`·`fileUrl`·`expiresAt`)는 그대로지만, **업로드 PUT에서 `Authorization` 헤더를 빼야 한다** — 프론트가 바꿔야 하는 유일한 지점이다. 지금부터 "`uploadUrl`이 이 서버의 `/v1/files/local-upload/`를 가리킬 때만 Bearer를 붙인다"로 분기해 두면 전환 때 프론트 코드 변경이 없다. 전환 시 위 로컬 임시 엔드포인트 2개는 제거된다.

### dashboard

2·3·4팀 Service를 모두 호출한다. **3팀 작업 중 마지막.**

| 메서드 | 경로 | 기능 | Request | Response | 비고 |
|---|---|---|---|---|---|
| GET | `/v1/dashboard` | 대시보드&nbsp;통합 | | `DashboardResponse` | 7.3 / 7.1.1 |

---

## §11. 로그인 (auth) — 1팀

인증이 필요 없는 유일한 그룹. 2026-10-05에 `API 초안` 로그인 4건(2026-09-28 검토 반영본)과 대조했다.

| 메서드 | 경로 | 기능 | Request | Response | 응답 코드 | 비고 |
|---|---|---|---|---|---|---|
| POST | `/v1/auth/kakao?code=` | 카카오&nbsp;인증 (로그인&nbsp;/&nbsp;신규&nbsp;판별) | `UserOAuth2CodeRequest` (Query) | `KakaoAuthResponse` | 200, 400 | 계정을 만들지 않는다. 기존 회원이면 로그인 토큰, 신규면 `signupToken` |
| POST | `/v1/auth/kakao/signup` | 카카오&nbsp;가입&nbsp;완료 (약관&nbsp;동의) | `KakaoSignupRequest` | `AuthTokenResponse` | 201, 400, 401, 409 | 이 요청에서 계정이 생성된다 |
| POST | `/v1/auth/signup` | 이메일&nbsp;회원가입 | `UserSignupRequest` | `AuthTokenResponse` | 201, 400, 409 | 카카오 회원과 중복 X (카카오 계정은 `email`이 null) |
| POST | `/v1/auth/login` | 이메일&nbsp;로그인 | `UserLoginRequest` | `AuthTokenResponse` | 200, 400, 401 | |

> `AuthTokenResponse(accessToken, userId, nickname, onboardingCompleted)`를 카카오 가입 완료·이메일 가입·이메일 로그인 3곳이 공유하고, 카카오 인증은 `KakaoAuthResponse.auth`에 같은 객체를 담는다. 리프레시 토큰 없음 (`docs/conventions/dto-naming.md` §1).

> **2026-09-28 결정: 카카오 가입 2단계 분리 (3건 → 4건).** 가입 전에 개인정보 처리방침 동의를 받아야 하는데 카카오 인가 코드는 1회용이라, 동의 후 같은 코드로 다시 호출할 수 없다. 그래서 카카오 인증은 계정을 만들지 않고 신규 회원에게 서버가 서명한 `signupToken`(10분 만료)을 내려주며, 프론트가 약관 동의 후 `POST /v1/auth/kakao/signup`으로 가입을 완료한다.

**에러 코드**

| 코드 | 상태 | 발생 |
|---|---|---|
| `VALIDATION_FAILED` | 400 | 4건 공통. `code` 누락·공백, 이메일 형식 오류, 비밀번호 규칙 위반(가입만), 빈 이름, `privacyAgreed`가 true가 아님, `signupToken` 누락 |
| `OAUTH_FAILED` | 400 | 카카오 인증. 인가 코드 만료·오류로 토큰 교환이나 사용자 조회에 실패 |
| `LOGIN_FAILED` | 401 | 이메일 로그인. 이메일 없음·비밀번호 불일치·카카오 전용 계정·탈퇴/정지 계정을 전부 같은 코드로 응답 (계정 존재 여부 비노출) |
| `INVALID_SIGNUP_TOKEN` | 401 | 카카오 가입 완료. 가입용 토큰 만료·위조 |
| `EMAIL_DUPLICATED` | 409 | 이메일 회원가입. 이미 가입된 이메일 |
| `ALREADY_REGISTERED` | 409 | 카카오 가입 완료. 토큰 안의 `kakao_id`로 이미 가입됨 (같은 토큰 재사용 등) |

---

## 알려진 공백 · 검토 필요

| # | 항목 | 내용 |
|---|---|---|
| 1 | DTO 필드 정의 | `docs/specs/dto-spec.md`에 정리 완료. `API 초안`에는 DTO 정보가 없어 기존 정의를 그대로 유지했다. 일부 DTO 페이지는 미작성 |
| 2 | 합주(rehearsal) 제외 | `API 초안`의 합주 행이 `v1/fee/...`(`studentFeeId`) 등 무관한 경로에 엔드포인트 누락까지 있어 이번 반영에서 제외했다(§3). 팀 정리 후 별도 반영 필요 |
| 3 | 총 개수 | 파일 도메인에 `DELETE /v1/files/{fileId}`가 추가되며 전체 개수가 바뀌었다. `docs/conventions/domains.md`·`docs/conventions/dto-naming.md`의 총계 갱신 필요 |
| 4 | performance 6 vs 7 | ✅ 해소(§8) — 원본 행 수는 7(셋리스트 확정 포함), `performance` 도메인 소유 개수는 6. **2026-10-30 갱신(이슈 #85)**: 공연 셋리스트 조회·넘기기가 생기고 셋리스트 3개를 performance가 받게 돼 `performance` 소유는 9. **2026-11-02**: 셋리스트 확정 해제가 더해져 10 |
| 5 | song 3 vs 2 | 여전히 미해소(§9) — `API 초안`도 song·setlist 소유를 구분하지 않는다 |
| 6 | 위치 불명 메모 | 원본의 "모임장" 단독 메모 2건은 인접 행(공연 생성·곡 검색)에 붙어 있었으나 대상 불확실 |
| 7 | 내보내기 PATCH | ✅ 해소 — 팀·모임 모두 `/status` 접미사로 확정, 노션도 2026-09-11 수정 반영(§2, §7) |
| 8 | 모임원 목록 응답 | `GroupMembersResponse` → `PageResponse<>` 개명 이력은 `API 초안`에 DTO 정보가 없어 갱신되지 않았다. 완성형은 `PageResponse<GroupMemberResponse>` (§7 §) |
| 9 | 경로 단수형 예외 | `join-request` · `recruit-post` 2개는 `API 초안`을 신뢰해 단수형 그대로 반영(2026-09-11 팀 확인). `api-conventions.md`의 "리소스는 복수형" 규칙의 명시적 예외로 기록 |
| 10 | 파일 삭제 신규 | `DELETE /v1/files/{fileId}`가 `API 초안`에서 새로 확인됨(§10). DTO·구현 담당 확인 필요 |
| 11 | 공고 세션별 마감 | 공고 `status`는 공고 전체에 하나뿐이라 세션별(예: BASS만 마감) 마감이 불가능하고, `wanted_slots`(instrument + count)에도 세션별 마감·충원 상태를 담을 자리가 없다. 세션별 현황(`filledCount`)은 `join_requests`의 `recruit_post_id` + `instrument` + `status=APPROVED` 집계로 스키마 변경 없이 가능하나, 세션별 마감 상태는 `wanted_slots` JSON 구조 변경이라 ERD 변경 절차(절대 규칙 5)가 필요하다. 화면 기획 확인 후 join 도메인 착수 시 함께 결정. 같은 뿌리로 마감된 공고의 수정(`PUT`)은 `409 INVALID_STATE`로 막았다(2026-10-08 결정). 세션별 마감을 도입하면 세션 단위로 다시 정한다 |
| 12 | 내 팀 목록 신규 | ✅ 확정(2026-10-31) — 노션 `API 초안`에 `[추가 필요]`로 올라온 `GET /v1/users/me/teams`(§2 ‡)를 2팀 소유로 확정하고 `docs/conventions/domains.md`·`docs/conventions/dto-naming.md` 총계(73 → 74)에 반영했다. 노션 쪽은 제목의 `[추가 필요]`와 담당자·진행 상태 정리가 남아 있다 |
| 13 | 공연 목록 `status` 필터 | ✅ 결정(2026-11-02, PR #84 리뷰) — 쿼리 이름을 `period`로 바꾸고 공연 상태로 묶는다(`UPCOMING`=`PLANNED`·`RECRUITING`, `PAST`=`DONE`·`CANCELED`, §8 ※). 응답 `status`는 실제 상태. 노션 정정 완료(2026-11-02). 원래 내용: `API 초안` 모임 공연 목록의 쿼리 `status`와 응답 `status` 예시가 `UPCOMING`·`PAST`인데 `performances.status`(`PLANNED|RECRUITING|DONE|CANCELED`)에 없는 값이다. 다가올·지난 공연을 나누는 기준(공연일 vs 상태)부터 정해야 해서 필터는 반영하지 않았었다 |
| 14 | 공연 상태 값 | `API 초안` 공연 상태 변경의 값 `PLANNED`·`ONGOING`·`ENDED`·`CANCELLED`가 ERD(`PLANNED|RECRUITING|DONE|CANCELED`)와 다르고, 응답에 `updatedAt`, 403 코드가 `NOT_GROUP_ADMIN`(모임 코드는 `NOT_GROUP_OWNER`)이다. 상태 변경 구현 때 결정 |

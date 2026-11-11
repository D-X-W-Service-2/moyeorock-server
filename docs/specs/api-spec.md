# API 명세 — 전 도메인 통합

- **원본**: Notion `API 명세서 수정안` 페이지의 `API 초안` 데이터베이스 (2026-09-11 대조 완료)
- **버전**: 전 도메인 `/v1/`. **예외 없다.** 합주(rehearsal, §3)만 초안 정리 전까지 `/v0/`를 유지했는데, 초안이 `/v1/`로 정리된 것을 확인하고 2026-10-05에 반영했다.
- **절 번호**: 이 파일과 `docs/specs/dto-spec.md`에서만 쓰는 좌표다
- **인증**: 모든 API는 유저 인증 필요. 예외(auth 4개)는 §11에 별도 표기
- **DTO 필드 정의**: `docs/specs/dto-spec.md` 참조. `API 초안`에는 DTO 정보가 없어 DTO 이름은 기존 `docs/conventions/dto-naming.md` 정의를 그대로 따른다. `*` 표시 Response는 원본 표에 비어 있으나 DTO 페이지의 이름·태그로 매칭한 것이다
- **경로 표기**: 공고는 단수형 `recruit-post`다. `docs/conventions/api-conventions.md` §5 "리소스는 복수형" 규칙과 어긋나지만, 초안 값을 그대로 신뢰해 반영하기로 했다(2026-09-11 팀 확인, 2026-11-03 현행 유지). 가입 신청은 초안이 처음엔 단수형(`join-request`)이었다가 복수형(`join-requests`)으로 바뀌어 2026-11-03에 복수형으로 맞췄다. 다른 리소스(`teams` `groups` `invitations` `bookmarks` 등)도 복수형이다 — `recruit-post` 하나만 예외다. 노션 `API 초안`의 공고 행은 아직 복수형(`/v1/recruit-posts`)으로 남아 있어 노션 쪽 정리가 필요하다

---

## §1. 사용자 (user) — 1팀

| 메서드 | 경로 | 기능 | Request | Response |
|---|---|---|---|---|
| GET | `/v1/users/me` | 내&nbsp;프로필&nbsp;조회 | | `UserMeResponse` |
| PUT | `/v1/users/me` | 프로필&nbsp;설정&nbsp;/&nbsp;수정 | `UserUpdateRequest` | `UserMeResponse` |
| DELETE | `/v1/users/me` | 탈퇴 | | `UserWithdrawResponse` * |
| POST | `/v1/users/me/onboarding` | 온보딩&nbsp;등록 | `OnboardingCreateRequest` | `UserMeResponse` ‡ |
| PUT | `/v1/users/me/instruments` | 세션/실력&nbsp;수정 | `UserInstrumentUpdateRequest` | `UserInstrumentsResponse` |
| GET | `/v1/users/me/activities` | 내&nbsp;활동 | | `UserActivityResponse` ◇ |
| GET | `/v1/users/{id}` | 타인&nbsp;프로필&nbsp;조회 | | `UserProfileResponse` § |
| GET | `/v1/users/search?nickname=&page=&size=` ¶ | 사용자&nbsp;검색 | | `PageResponse<UserSummaryResponse>` |
| GET | `/v1/users/nickname/check?nickname=` | 닉네임&nbsp;중복&nbsp;확인 | | `NicknameCheckResponse` † |

> ¶ 초안에서 `page`·`size` 페이징 파라미터가 명시적으로 추가됐다(2026-09-11).
> † 원래 Notion `API 명세서 v0`에는 없던 엔드포인트였으나(`docs/conventions/dto-naming.md` §2가 먼저 정의), `API 초안`에 쿼리 파라미터까지 포함해 반영된 것을 2026-09-11 확인했다.
> `/v1/users/search`는 원본에서 팀 섹션에 있었지만 user 도메인 소유다.
> `PUT /v1/users/me/instruments`의 응답이 `UserInstrumentsResponse`다 (`docs/conventions/dto-naming.md` §2).
> § 타인 프로필 조회는 로그인 필수다(이슈 #90 P-5). 탈퇴 회원과 없는 id는 404 `USER_NOT_FOUND`, 정지(SUSPENDED) 회원은 노출한다(P-7, 목록 조회 `getSummaries`와 맞춤). 본인 id로 불러도 같은 형태다(P-8). `activities`는 `isActivityPublic=false`면 `null`이고(P-3), 공개면 내 활동과 같은 조립이다(비공개 사용자는 활동 조회 자체를 하지 않는다). id가 숫자가 아니면 400 `VALIDATION_FAILED`.
> ◇ 내 활동은 다른 `/me`와 같이 ACTIVE 사용자만 통과한다(아니면 404 `USER_NOT_FOUND`). 세 목록은 각 팀 Service가 ACTIVE 필터·정렬까지 맡고 user는 조립만 한다(이슈 #90). `teams`는 2팀 `TeamService.findMemberships`(최근 합류 순). `groups`는 4팀 `GroupMemberService.findMyGroupSummaries`(가입 최신순), `performances`는 4팀 `PerformanceService.findActivitiesByUserId`(진행 중인 공연만, 공연일 가까운 순, 내 팀마다 한 줄)다(이슈 #95 · #126, 2026-11-03).
> \* 탈퇴는 직책 보유자에게 막힌다(10/7 결정, 10/30 재확인): 팀장인 활동 중인 팀이나 모임장인 활동 중인 모임이 있으면 `409 WITHDRAW_BLOCKED_BY_ROLE`이고 `message`에 어느 팀·모임인지 담는다(예: `팀 '무명밴드'의 팀장을 위임하고, 모임 '홍대 밴드부'의 모임장을 위임하거나 모임을 해체한 뒤 탈퇴할 수 있습니다.`). 통과하면 탈퇴자의 대기 중 가입 신청은 `CANCELED`, 받은 초대는 `REJECTED`로 닫고(알림 없음, 이슈 #136) 모임원 행과 팀원 행을 전부 `LEFT`로 바꾼다(그대로 두면 `memberCount`와 목록이 어긋난다). 마지막으로 개인 연습 녹음(분석 중 포함)·분석 결과·녹음 파일을 전부 지운다(4팀 `PracticeRecordingService.deleteAllOf`, PR #157 리뷰 2026-11-11). 판정·정리는 2팀 `TeamService.findLedTeams`·`leaveAllTeams`(PR #97)와 4팀 `GroupMemberService.findOwnedGroups`·`leaveAllGroups`(#112). Notion 탈퇴 페이지에 에러 응답 추가 필요(2026-11-02).
> ‡ 온보딩 등록은 재호출이 멱등(값만 갱신)이라 201이 아니라 200이다. 명세 표에 응답 코드가 없어 서버가 정한 값이다. Notion 반영 필요. 프로필 수정·온보딩 응답은 갱신된 `UserMeResponse` 전체다(PR #57 Service 반환 그대로, Controller는 #89).

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
> 팀원 추천(AI)은 구현했다(2026-11-08, 이슈 #150). **팀장만** 부를 수 있고(노션 Actor `팀장`), 아니면 `403 NOT_TEAM_LEADER`, 해체된 팀은 `409 INVALID_STATE`, 없는 팀은 `404 TEAM_NOT_FOUND`다. `instrument` 누락·없는 값은 `400 VALIDATION_FAILED`. 후보는 활동 중·추천 허용(`is_recommendable`)·그 악기 보유·이 팀 팀원이 아닌 사용자 중 무작위 최대 200명이고, 응답은 점수 높은 순 **최대 10명**이다(후보가 없으면 빈 배열, AI를 부르지 않는다). AI 서버 호출이 실패하거나 시간이 초과되거나 주소가 없으면 **`503 RECOMMENDATION_UNAVAILABLE`**이다 — 노션에 없던 에러라 `API 초안` 팀원 추천 페이지에 추가했다. 호출 형식은 §12.
> 팀을 개별로 해체(`PATCH /v1/teams/{id}/status`)하면 그 팀이 공연 셋리스트로 넘긴 곡(`setlists` 행)과 그 팀의 선택곡(`team_songs` 행)을 함께 삭제한다. 셋리스트 행이 남으면 같은 공연의 다른 팀이 그 곡을 넘길 수 없고, 선택곡 행이 남으면 다른 팀에게 "이미 골랐어요"가 계속 뜨기 때문이다(4팀 결정, 이슈 #85·PR #87 리뷰). 공연 종료·취소로 인한 일괄 해체에서는 둘 다 삭제하지 않는다. 삭제는 4팀 `SetlistService.deleteByTeam`을 해체와 같은 트랜잭션에서 부른다(2026-11-04 연결, 이슈 #130).
> 해체된 팀의 상세·팀원 목록은 읽기 전용으로 열려 있고, 상세의 `status`가 `DISBANDED`다(2026-11-02 결정, 4팀 요청 — 끝난 공연 페이지의 팀 카드가 연결된다). 수정·해체·팀원 변경·탈퇴는 해체된 팀에서 막힌다. 해체된 팀은 요약 조회(`getSummaries`)에서는 여전히 빠지고, 4팀용 `getSummariesIncludingDisbanded`로만 담긴다.
> 팀원 역할 변경과 내보내기·탈퇴에서 경로의 `{userId}`가 그 팀의 활동 중인 팀원이 아니면(속한 적이 없거나 이미 탈퇴·강퇴된 경우) `404 TEAM_MEMBER_NOT_FOUND`다. 모임의 `GROUP_MEMBER_NOT_FOUND`와 같은 상황이라 맞췄다(PR #86 리뷰).
> 팀원 내보내기·탈퇴는 `API 초안`에서도 `/status` 접미사가 붙은 완성형으로 확인됐다(2026-09-11) — 과거 DELETE→PATCH 전환 미완 문제는 해소됐다. 상태값은 `LEFT`(본인 탈퇴)·`REMOVED`(강퇴)이며, 노션 행 제목의 `KICKED`는 표기 오류다.
---

## §3. 합주 (rehearsal) — 2팀

2026-09-11 대조 때는 `API 초안`의 합주 행이 `v1/fee/...` · `{studentFeeId}` 등 무관한 경로였고 일부는 비어 있어 이 절만 `/v0/`를 유지했다. **2026-10-05 재대조에서 6개 전부 `/v1/`로 정리된 것을 확인해 반영했다** — 경로만 바뀌었고 기능·DTO는 그대로다. **2026-11-02 구현을 마쳤다**(이슈 #94). 구현에서 정한 규칙은 표 아래 메모에 적었다.

| 메서드 | 경로 | 기능 | Request | Response |
|---|---|---|---|---|
| GET | `/v1/teams/{teamId}/rehearsals?from=&to=` | 팀&nbsp;합주&nbsp;일정&nbsp;목록 | | `RehearsalsResponse` |
| POST | `/v1/teams/{teamId}/rehearsals` | 합주&nbsp;일정&nbsp;생성 | `RehearsalCreateRequest` | |
| GET | `/v1/rehearsals/{id}` | 합주&nbsp;일정&nbsp;상세 | | `RehearsalDetailResponse` |
| PUT | `/v1/rehearsals/{id}` | 합주&nbsp;일정&nbsp;수정 | `RehearsalUpdateRequest` | |
| DELETE | `/v1/rehearsals/{id}` | 합주&nbsp;일정&nbsp;삭제 | | |
| GET | `/v1/users/me/rehearsals?from=&to=` | 내&nbsp;합주&nbsp;일정 | | `RehearsalsResponse` |

> 생성·수정 응답은 `RehearsalDetailResponse`(생성은 201), 삭제 응답은 204가 아니라 `200` + `DeleteResponse`다.
> 권한: 생성·목록·상세는 그 팀의 활동 중 팀원(아니면 `403 NO_PERMISSION`, 없는·해체된 팀은 `404 TEAM_NOT_FOUND`). **수정·삭제는 작성자나 팀장**이다 — 노션에 규칙이 없어 2026-11-02에 정했다. 없는 합주는 `404 REHEARSAL_NOT_FOUND`.
> `from`·`to`는 `2026-09-01` 형식의 날짜이고 둘 다 포함한다. 둘 다 생략하면 이번 달 한 달치, 하나만 보내거나 `to`가 `from`보다 앞서거나 `from`의 석 달 뒤 날짜를 넘으면 `400 VALIDATION_FAILED`다 — 노션에는 필수 여부·생략 시 동작이 비어 있어 정했다.
> 종료 시각이 시작 시각보다 늦지 않으면 `400 VALIDATION_FAILED`다(생성·수정). 권한 확인(팀원, 수정은 작성자·팀장)이 먼저라서 권한이 없으면 입력이 틀려도 403이다. 메모는 20,000자 이하다(공지 본문과 같은 기준, `TEXT` 컬럼 크기를 고려). 내 합주 일정은 활동 중인 팀의 합주만 모으고 해체된 팀은 빠진다.

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
> Actor(Notion `API 초안`): 작성·수정·마감은 `모임장 / 팀장`, 목록·상세는 공통. **현재 구현(2026-11-03)**: 작성·수정·마감은 대상 **팀의 현재 팀장 / 모임의 현재 모임장**만 할 수 있다(작성자가 아니라 현재 권한자 기준 — 작성자가 팀장에서 빠지면 더는 고칠 수 없다). 작성할 때 대상 팀·모임 존재도 함께 확인한다: 없거나 해체된 대상 `404 TEAM_NOT_FOUND`·`GROUP_NOT_FOUND`, 권한 없음 `403 NOT_TEAM_LEADER`·`NOT_GROUP_OWNER`. 모임이 해체되면 그 모임의 모집 중 공고는 자동 마감된다. `canEdit`은 보는 사람이 현재 팀장/모임장인지다.
> 마감(`CLOSED`)된 공고는 수정(`PUT`)할 수 없다 — 작성자라도 `409 INVALID_STATE`다(권한 검사가 먼저라 작성자가 아니면 `403 NOT_POST_AUTHOR`). 명세에는 없는 서버 정책으로, 마감 뒤 모집 세션·인원이 바뀌어 이미 들어온 신청과 어긋나는 것을 막는다 — Notion 반영 필요.
> `wantedSlots`에 같은 `instrument`가 두 번 오면 `400 VALIDATION_FAILED`다(예: BASS 1명 + BASS 2명). 명세에는 없는 서버 검증으로, 세션별 집계·마감 기준이 모호해지는 것을 막는다 — Notion 반영 필요.
> 경로가 단수형 `recruit-post`다 — 상단 "경로 표기" 참고.
> ⚠️ DTO 이름(`RecruitPostStatusUpdateRequest` 등)은 `API 초안`에 없어 기존 `docs/conventions/dto-naming.md` 정의를 그대로 썼다. 과거 노션 `API 명세서 v0`이 마감·삭제 Request를 `RecruitPostStatusResponse`로 오기했던 문제는 이 표와 무관하다.

---

## §5. 가입 신청 (join · JoinRequestController) — 3팀

팀 가입, 모임 가입, 팀 제안 **전부 이걸로 처리**한다. 초대(§6)와 엔티티·테이블(`join_requests`)을 공유하고 `direction=APPLY`로 구분한다.

| 메서드 | 경로 | 기능 | Request | Response | 비고 |
|---|---|---|---|---|---|
| POST | `/v1/join-requests` | 가입&nbsp;신청 | `JoinRequestCreateRequest` | `JoinRequestResponse` (201) | |
| GET | `/v1/join-requests` ¶ | 받은&nbsp;신청&nbsp;목록 | | `PageResponse<JoinRequestResponse>` | 모임장/팀장 |
| GET | `/v1/users/me/join-requests?status=` | 내&nbsp;신청&nbsp;목록 | | `PageResponse<JoinRequestResponse>` | |
| PATCH | `/v1/join-requests/{id}/status` | 신청&nbsp;승인·거절·취소 | `JoinRequestStatusUpdateRequest` | `JoinApprovedResponse`(승인) / `JoinDecisionResponse`(거절·취소) * | 승인·거절 모임장/팀장, 취소 신청자 본인 |

> ¶ 받은 신청 목록의 쿼리 파라미터: `targetType` · `targetId` · `status`. **`targetType`·`targetId`는 필수다**(권한을 대상 단위로 확인하므로. Notion은 "전부 선택"이라 적었지만 대상 없이는 모임장/팀장을 정할 수 없다 — 명세에 없는 서버 정책). 정렬은 최신순 고정.
> **상태 변경**: 바디 `{"status": "APPROVED" | "REJECTED" | "CANCELED"}`(Notion 초안에는 바디 설명이 없어 서버가 정했다). `PENDING`은 400, 이미 처리된 신청은 409 `INVALID_STATE`, 권한 없는 사람은 403(`NOT_TEAM_LEADER`·`NOT_GROUP_OWNER`, 취소는 신청자가 아니면 `NO_PERMISSION`)이다. 권한을 먼저 보고 상태를 본다. 승인하면 팀원/모임원(`MEMBER`)이 된다. 승인 시점에 이미 소속이면 409 `ALREADY_MEMBER`이고 신청은 대기로 남는다.
> **공연 팀**: 대상 팀이 공연에 속한 팀이면 신청자가 그 공연을 연 모임의 활동 모임원이어야 한다. 아니면 `403 NOT_PERFORMANCE_GROUP_MEMBER`다(#96). 신청을 만들 때와 승인할 때(`TeamService.addMember`) 두 번 확인한다 — 그사이 모임을 나갔을 수 있어서다. 초대도 같다(초대받는 사람이 모임원이어야 하고, 수락 때 다시 확인).
> **신청 검증**: 팀 대상은 `instrument` 필수(없으면 400), 모임 대상은 `instrument`를 저장하지 않는다. `message`는 선택(최대 500자). 같은 사람·같은 대상에 대기(`PENDING`) 신청이나 초대가 있으면 409 `JOIN_REQUEST_DUPLICATED`(신청·초대를 가리지 않음), 이미 팀원/모임원이면 409 `ALREADY_MEMBER`, 팀장/모임장이 자기 팀·모임에 신청하면 400이다. 거절·취소된 뒤에는 다시 신청할 수 있다.
> **공고를 거친 신청(`recruitPostId`)**: 없는 공고 404 `RECRUIT_POST_NOT_FOUND`, 마감된 공고 409 `INVALID_STATE`, 요청 대상과 다른 대상의 공고·모집하지 않는 세션(팀 공고)·내가 쓴 공고는 400이다. **정원은 검사하지 않는다** — 신청은 항상 받고 팀장이 승인 때 판단한다. 공고가 마감된 뒤에도 이미 들어온 대기 신청은 승인할 수 있다. 공고 상세의 `wantedSlots[].appliedCount`는 그 세션의 대기 신청 수, `myJoinRequest`는 내가 그 공고에 낸 가장 최근 신청(`{id, status}`, 없으면 `null`)이다.
> **알림**(트랜잭션 안에서 이벤트 발행): 팀 신청 → 팀장 `TEAM_APPLY`, 모임 신청 → 모임장 `RECRUIT_POST_APPLIED`(모임용 전용 타입이 없어 대신 썼다), 승인·거절 → 신청자 `JOIN_REQUEST_APPROVED`·`JOIN_REQUEST_REJECTED`. 취소는 알리지 않는다.
> 응답의 `target`은 팀이면 이름·지역·장르, 모임이면 이름만 채운다(`region`·`genres`는 `null`, 4팀 일괄 조회가 생기면 채움). 해체된 대상은 내 신청 목록에서도 빼지 않고 `target`에 `id`만 남는다. 해체된 대상의 처리: 초대 거절(`DECLINED`)·신청 취소(`CANCELED`)는 되고(알림 문구는 "해체된 팀"·"해체된 모임"), 초대 수락·신청 승인은 안 된다(팀 `409 INVALID_STATE`, 모임 `404 GROUP_NOT_FOUND`). 팀장·모임장 쪽 승인·거절·취소와 받은 신청·보낸 초대 목록은 대상 확인에서 404다. `target`이 id만 오면(`name == null`) 해체된 대상이다 — Notion 반영 필요. `applicant`는 탈퇴한 신청자면 `null`이다.
> 경로는 복수형 `join-requests`다 — 상단 "경로 표기" 참고. `join_requests` 테이블명은 그대로다.

---

## §6. 초대 (join · InvitationController) — 3팀

`direction=INVITE`. 별도 엔티티·테이블을 만들지 않는다 (`docs/conventions/domains.md` 참조).

| 메서드 | 경로 | 기능 | Request | Response |
|---|---|---|---|---|
| POST | `/v1/invitations` | 초대&nbsp;보내기 | `InvitationCreateRequest` | |
| GET | `/v1/invitations` ¶ | 보낸&nbsp;초대&nbsp;목록 | | `PageResponse<InvitationResponse>` |
| GET | `/v1/users/me/invitations?status=` | 받은&nbsp;초대&nbsp;목록 | | `PageResponse<InvitationResponse>` |
| PATCH | `/v1/invitations/{id}/status` | 초대&nbsp;수락·거절·취소 | `InvitationStatusUpdateRequest` | `JoinApprovedResponse`(수락) / `JoinDecisionResponse`(거절·취소) * |

> ¶ 보낸 초대 목록의 쿼리 파라미터: `targetType` · `targetId` · `status`. **보낸 목록은 `targetType`·`targetId`가 필수**(팀장/모임장 권한을 대상 단위로 확인). 받은 목록은 내 초대만 보이므로 `targetType`·`targetId`(함께 줘야 함)·`status`는 거르는 용도의 선택이다(Notion은 받은 목록도 대상을 필수로 적었으나 복사된 표로 보인다 — 서버 정책).
> **상태 변경**: 바디 `{"status": "ACCEPTED" | "DECLINED" | "CANCELED"}`. 수락·거절은 초대받은 본인, 취소는 팀장/모임장이 한다. 수락하면 팀원/모임원이 되고 응답 `status`는 `APPROVED`(수락)·`REJECTED`(거절)·`CANCELED`로 가입 신청과 같은 값이다. 신청 id로 초대 경로를 부르면(또는 반대) 404 `JOIN_REQUEST_NOT_FOUND`다.
> **초대 검증**: 팀장/모임장만 보낼 수 있다(403·404). 공연 팀이면 초대받는 사람이 그 공연을 연 모임의 모임원이어야 한다(`403 NOT_PERFORMANCE_GROUP_MEMBER`). 자기 자신 400, 없는 사용자 404 `USER_NOT_FOUND`, 이미 소속 409 `ALREADY_MEMBER`, 대기 중인 신청·초대가 있으면 409 `JOIN_REQUEST_DUPLICATED`, 팀 초대는 `instrument` 필수다.
> **알림**: 초대 → 초대받은 사람 `TEAM_INVITE`·`GROUP_INVITE`, 수락·거절 → 초대한 사람 `INVITATION_ACCEPTED`·`INVITATION_DECLINED`. 취소는 알리지 않는다. 경로는 모두 복수형 `invitations`다.

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
| PUT | `/v1/performances/{performanceId}` | 공연&nbsp;정보&nbsp;수정 | `PerformanceUpdateRequest` | `PerformanceDetailResponse` | 모임장 · 7.4 공지 + 공연 포스터 · 끝난 공연(`DONE`·`CANCELED`)은 409 |
| PATCH | `/v1/performances/{performanceId}/status` | 공연&nbsp;상태&nbsp;변경 | `PerformanceStatusUpdateRequest` | `PerformanceStatusResponse` * | 모임장 · `DONE`·`CANCELED` 시 소속 팀 해체 → 2팀 `TeamService` 호출 · 끝난 공연은 409 ⊕ |
| POST | `/v1/performances/{performanceId}/teams` | 공연&nbsp;내&nbsp;팀&nbsp;생성 | `PerformanceTeamCreateRequest` | TeamDetailResponse 형태 * | 모임원 · 7.1 생성자 = OWNER · 2팀 `TeamService` 호출 · `PLANNED`·`RECRUITING` 공연만 ⊕ |
| GET | `/v1/performances/{performanceId}/setlist` | 공연&nbsp;셋리스트&nbsp;조회 ◆ | | `SetlistResponse` | 모임원 · 확정 전엔 넘겨진 곡 전체, 확정 후엔 확정곡만 |
| PUT | `/v1/performances/{performanceId}/setlist/teams/{teamId}` | 셋리스트로&nbsp;넘기기 ◆ | `SetlistSubmitRequest` | `SetlistResponse` | 공연 팀 팀장 · 확정 후 409(해제하면 다시 가능) ♪ |
| PUT | `/v1/performances/{performanceId}/setlist` | 공연&nbsp;셋리스트&nbsp;확정 ◆ | `SetlistConfirmRequest` | `SetlistResponse` | 모임장 · 넘겨진 곡만, 요청에 없는 곡은 빠짐 · 끝난 공연은 409 ♪ |
| PATCH | `/v1/performances/{performanceId}/setlist/unconfirm` | 공연&nbsp;셋리스트&nbsp;확정&nbsp;해제 ◆ | — | `SetlistResponse` | 모임장 · 미확정·끝난·취소된 공연이면 409 |

> ◆ **2026-10-30 셋리스트 흐름 변경(이슈 #85)** — 팀 선택곡(`team_songs`)과 공연 셋리스트(`setlists`)를 테이블로 나누고(`erd.md` §15·§18), 공연 단위 셋리스트 4개(확정 해제 포함)를 **performance가 받는다**(`docs/conventions/domains.md` "헷갈리는 경계"). 확정 시각이 `performances.setlist_confirmed_at`이라 `PerformanceService`가 공연·확정 여부·모임장을 확인하고 행 처리는 `SetlistService`에 맡긴다. 조회·넘기기는 신규, 확정은 기존 행(`API 초안` 2026-10-30 수정 반영). 확정 요청 DTO 이름은 `dto-naming.md` §9의 `SetlistConfirmRequest`다(노션도 2026-11-02에 `SetlistUpdateRequest`에서 맞췄다). **확정 해제는 2026-11-02에 추가했다**(PR #87 리뷰) — 확정 뒤에는 팀이 곡을 넘길 수 없고 모임장은 넘겨진 곡 안에서만 고를 수 있어, 해제가 없으면 확정 때 뺀 곡을 다시 넣을 길이 없었다. 해제하면 확정 시각과 `sort_order`를 비우고 팀이 다시 넘길 수 있다. 노션에 페이지를 새로 만들었고, 구현은 셋리스트 API 때 한다.
> ♪ **2026-11-02 셋리스트 구현 결정(이슈 #103)**
> - **넘기기 에러** — 그 팀 선택곡에 없는 곡 `409 SONG_NOT_SELECTED_BY_TEAM`, 같은 공연의 다른 팀이 이미 넘긴 곡 `409 SONG_ALREADY_IN_SETLIST`, 없는 곡 `404 SONG_NOT_FOUND`, 이 공연 소속이 아닌 팀 `404 TEAM_NOT_FOUND`, 확정된 셋리스트 `409 INVALID_STATE`
> - **확정 에러** — 그 팀이 넘기지 않은 곡 `409 SONG_NOT_TEAM_CANDIDATE`(노션 그대로), 이 공연 소속이 아닌 팀 `404 TEAM_NOT_FOUND`, **끝난 공연(`DONE`·`CANCELED`)은 확정도 `409 INVALID_STATE`**(해제와 같게 — 끝난 공연의 셋리스트는 기록). 빈 `items`, 같은 곡 중복, `sortOrder`가 1부터 빈틈없이(1..n) 매겨지지 않은 요청은 `400 VALIDATION_FAILED`(2026-11-03 — 프론트가 드래그 순서대로 1번부터 매겨 보낸다)
> - **동시성은 공연 행 잠금** — 넘기기·확정·해제는 공연 행을 잠그고 시작해 같은 공연에서 차례로 처리된다. 확정 뒤에 들어온 넘기기는 `409 INVALID_STATE`, 같은 곡을 뒤에 넘긴 팀은 `409 SONG_ALREADY_IN_SETLIST`(`UNIQUE(performance_id, song_id)`는 안전망). 같은 공연에 두 팀이 동시에 넘기는 것만으로는 실패하지 않는다(뒤 요청이 잠깐 기다린다, PR #111 리뷰)
> 이전 기록(2026-09-11): `API 초안`이 셋리스트 확정을 공연 도메인 행으로 적었지만 당시 소유는 setlist였다 — `team_songs` 하나에 팀 선택과 확정이 섞여 있어 한 서비스가 `UNIQUE`를 검증해야 했기 때문이다. 테이블을 나누면서 그 이유가 사라졌다.
> `API 초안`의 이 7개 행은 반영 당시(2026-09-10 이전) `인증 여부`·`진행 상태`가 비어 있었다. 모든 API는 인증이 필요하다는 원칙(상단 참고)에 따라 2026-09-11에 전부 "인증 필요"로 채우고 노션도 함께 수정했다.
> ※ **2026-10-30 대조(이슈 #83)** — `API 초안`(2026-10-08 수정)과 대조해 경로 변수를 `{performanceId}`로, 비고에 노션 Actor를 맞췄다. 어긋나는 곳은 이렇게 정했다.
> - **생성 응답은 `PerformanceDetailResponse`** — 노션 생성 응답(`id`·`title`·`description`·`performedAt`·`venue`·`posterImage`·`createdAt`)을 모두 포함하는 상세 응답을 쓴다(`docs/conventions/dto-naming.md` 규칙 3 "생성 응답은 상세 재사용")
> - **`venue`는 필수** — 노션 생성·수정 요청의 Required가 O다. `erd.md`의 "7.1.1 필수 X" 설명을 정정했다(컬럼은 NULL 허용 그대로)
> - **모임 공연 목록** — 다가올·지난 구분은 쿼리 `period`로 받는다(2026-11-02, PR #84 리뷰, 공백 #13). 기준은 공연일이 아니라 **공연 상태**다 — `performances.status`는 그대로 두고 목록에서만 `PLANNED`·`RECRUITING` → `UPCOMING`, `DONE`·`CANCELED` → `PAST`로 묶는다. `period`는 선택이고 없으면 전체, 없는 값은 `400 VALIDATION_FAILED`. 응답 항목의 `status`는 실제 공연 상태(`PLANNED` 등)다. 정렬은 고정이라 `sort`는 받지 않는다 — `UPCOMING`은 공연일 가까운 순, `PAST`와 전체는 공연일 최근 순, 공연일이 같으면 id 순(같은 방향). `page`·`size`는 기본·최대 20
>   - 모임장이 `DONE`으로 바꾸기 전에는 날짜가 지난 공연도 `UPCOMING`에 남는다. 화면 안내로 푼다
> - **이슈 #83 구현 범위** — 생성·목록·상세·수정 4개. 상세의 `teams`·`myTeamIds`는 이슈 #88에서 추가했고(아래 ⊕), `setlist`(확정곡만, 확정 전엔 빈 목록 — 단 확정 뒤 팀이 모두 해체돼도 빈 목록이라 확정 여부는 셋리스트 조회의 `confirmed`로 본다)는 이슈 #145에서 추가했다(`dto-spec.md` PerformanceDetailResponse)
> - **상태 변경** — 노션의 상태 값(`PLANNED`·`ONGOING`·`ENDED`·`CANCELLED`), 응답의 `updatedAt`, 에러 코드 `NOT_GROUP_ADMIN`이 ERD·모임 코드(`NOT_GROUP_OWNER`)와 다르다 → 이슈 #88에서 정했다(아래 ⊕, 공백 #14)
>
> ⊕ **2026-11-01 상태 변경·공연 내 팀 생성(이슈 #88)** — 2팀 `TeamService`(#86)로 상태 변경·공연 내 팀 생성과 상세의 `teams`·`myTeamIds`를 구현하며 이렇게 정했다.
> - **상태 값은 ERD enum 그대로**(`PLANNED|RECRUITING|DONE|CANCELED`). 노션 값(`ONGOING`·`ENDED`·`CANCELLED`)을 보내면 `400 VALIDATION_FAILED`다. 응답은 `{id, status}`(노션의 `updatedAt` 없음), 모임장이 아니면 `403 NOT_GROUP_OWNER`
> - **끝난 공연(`DONE`·`CANCELED`)은 상태를 바꿀 수 없다** — `409 INVALID_STATE`. 소속 팀이 이미 해체돼서 되돌려도 팀이 돌아오지 않는다. `PLANNED`↔`RECRUITING`은 자유롭고, 같은 값으로 바꾸면 그대로 200
> - **`DONE`뿐 아니라 `CANCELED`로 바꿔도 소속 팀을 모두 해체한다**(`TeamService.disbandAllByPerformance`, 같은 트랜잭션). 일괄 해체라 셋리스트·선택곡 행은 지우지 않는다(`docs/conventions/architecture.md` §6)
> - **공연 내 팀 생성은 `PLANNED`·`RECRUITING` 공연에서만** — 끝난 공연은 `409 INVALID_STATE`. 모임원이 아니면 `403 NOT_GROUP_MEMBER`. 공연 존재·권한·상태는 performance가 확인하고 저장은 `TeamService.createForPerformance`가 한다. 응답 `201` + `TeamDetailResponse`인데, 2팀 DTO에 `performance` 필드가 아직 없어 그 필드는 빠져 있다

---

## §9. 노래 (song) · 셋리스트 (setlist) — 4팀

### song

| 메서드 | 경로 | 기능 | Request | Response | 비고 |
|---|---|---|---|---|---|
| GET | `/v1/songs?keyword=&genre=&page=&size=` ¶ | 곡&nbsp;검색 | | `PageResponse<SongResponse>` | 세션별 난이도 포함 |
| GET | `/v1/users/me/songs/recommendations` | 내&nbsp;취향&nbsp;추천곡 | | `SongRecommendationResponse` | 6.2.1 세션별 실력·장르 반영 |
| GET | `/v1/teams/{teamId}/songs/recommendations` | 팀&nbsp;곡&nbsp;추천&nbsp;(AI) | | `TeamSongRecommendationResponse` | 7.2 · 팀원 |
| POST | `/v1/songs/sync` ⚙ | 곡&nbsp;수동&nbsp;동기화 | | `SongSyncResponse` | 운영용 · §12 |

> ¶ `API 초안`에서 `page`·`size` 페이징 파라미터가 명시적으로 추가됐다(2026-09-11).
> 곡 마스터(`songs`)는 화면에서 넣지 않는다 — 백엔드가 AI 서버 카탈로그를 받아와 채운다(§12, 이슈 #147).
> ⚙ 화면에서 부르는 API가 아니라 운영용이다(2026-11-11, 이슈 #162). 매일 04:00 동기화를 지금 바로 돌린다. 노션 `API 초안`에는 없다. 로그인한 사용자면 누구나 부를 수 있다. 이미 돌고 있으면 409 `SONG_SYNC_IN_PROGRESS`, AI 서버 주소가 없거나 호출이 실패하면 503 `SONG_SYNC_UNAVAILABLE`(실패 전에 저장한 페이지는 남는다).
> 🚧 추천 2개(내 취향 추천곡·팀 곡 추천)는 추천 방식(룰 기반/외부 모델)과 소유 도메인이 정해지지 않아 셋리스트 구현(이슈 #103) 범위에서 뺐다(2026-11-02).
> ⚠️ `docs/conventions/domains.md`는 song 2개로 집계한다. 팀 곡 추천을 song에 두면 3개가 되므로 **소유 도메인(song vs setlist) 확인 필요.** `API 초안`은 이 3개를 모두 "노래 / 셋리스트"로 묶어만 두어 이 논쟁을 해소하지 못한다. 2026-10-30에 이름을 "팀 셋리스트 추천"에서 바꿨다 — 팀 쪽에서는 "셋리스트"라는 말을 쓰지 않는다(이슈 #85).

### setlist

`team_songs`(팀 선택곡)·`setlists`(공연 셋리스트) 두 테이블을 소유한다(2026-10-30, 이슈 #85). 엔드포인트는 팀 선택곡 저장·조회 2개이고, 공연 셋리스트 조회·넘기기·확정·확정 해제는 §8에서 performance가 받는다. performance·team은 두 테이블을 직접 건드리지 않고 `SetlistService`를 부른다.

| 메서드 | 경로 | 기능 | Request | Response | 비고 |
|---|---|---|---|---|---|
| PUT | `/v1/teams/{teamId}/songs` | 팀&nbsp;선택곡&nbsp;저장 | `TeamSongsSaveRequest` | `TeamSongsResponse` | 팀장 · 독립 팀 포함. 기존 선택곡 전체 대체 |
| GET | `/v1/teams/{teamId}/songs` | 팀&nbsp;선택곡&nbsp;조회 ♩ | | `TeamSongsResponse` | 팀원 · 독립 팀 포함 |

> 2026-10-30 변경: 경로 `/v1/teams/{id}/setlist` → `/v1/teams/{teamId}/songs`, 이름 "팀 셋리스트 저장" → "팀 선택곡 저장", 요청에서 `sortOrder`·`progress`를 뺐다(`API 초안` 수정 반영). 선택곡을 바꿔도 이미 넘긴 셋리스트는 바뀌지 않고, 셋리스트 확정 뒤 곡 추가는 UI에서 막는다.
> ♩ **2026-11-02 신규(이슈 #103)** — 노션 `API 초안`에 "[추가 필요] 팀 선택곡 조회"가 `GET /v1/teams/{teamId}/setlist`(팀원, 내용 없음)로 올라와 있었다. 팀 쪽에서는 "셋리스트"라는 말을 쓰지 않기로 해서 경로를 저장과 같은 `/songs`로 맞추고 응답은 저장 응답 `TeamSongsResponse`를 그대로 쓴다. 해체된 팀은 `404 TEAM_NOT_FOUND`, 팀원이 아니면 `403 NO_PERMISSION`(2팀 `TeamMemberService.ensureActiveMember`)
> 선택곡 저장 에러 — 없는 곡이 섞이면 `404 SONG_NOT_FOUND`, 같은 곡 중복·`songIds` 누락은 `400 VALIDATION_FAILED`, 팀장이 아니면 `403 NOT_TEAM_LEADER`, 해체·없는 팀 `404 TEAM_NOT_FOUND`

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

> **저장 대상 (2026-11-04 갱신)**: `USER`·`TEAM`·`SONG`을 저장할 수 있다. 곡은 없으면 `404 SONG_NOT_FOUND`이고, 곡 `target`은 `name`에 제목을 채우고 `region`·`genres`·`imageUrl`은 `null`이다(곡 축약형의 가수 `artist`는 `target`에 담을 필드가 없어 내려가지 않고, 장르는 축약형에도 없다). 팀은 없거나 해체된 팀이면 `404 TEAM_NOT_FOUND`이고, 자기 팀도 저장할 수 있다(사용자와 달리 집계에 본인이 섞이는 문제가 없다). 팀 `target`은 이름·지역·장르를 채우고 `imageUrl`은 `null`이다.
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
| DELETE | `/v1/files?fileUrl=` | 파일&nbsp;삭제 † | `fileUrl` (Query) | `DeleteResponse` |

**로컬 저장 전용 임시 엔드포인트** — AWS 비용 때문에 당분간 서버 로컬 디스크에 저장하기로 한 결정(이슈 #18) 때문에 생긴 것이다. S3로 전환하면 `LocalFileStorageClient`와 함께 **둘 다 제거한다.** `API 초안`에는 없다.

| 메서드 | 경로 | 기능 | Request | Response |
|---|---|---|---|---|
| PUT | `/v1/files/local-upload/**` | 업로드&nbsp;수신&nbsp;‡ | 파일 바이트(raw body) | — (`data: null`) |
| GET | `/v1/files/local/**` | 파일&nbsp;서빙&nbsp;‡ | | 원시 바이트 |

> † `API 초안`에서 새로 확인된 엔드포인트다(2026-09-11). 응답은 공용 `DeleteResponse`(`{id, deleted}`)를 쓴다. **2026-11-11 `DELETE /v1/files/{fileId}`에서 `?fileUrl=`로 바꿨다(이슈 #164)** — 프로필·커버·포스터는 `fileUrl`만 저장하고 조회 응답에도 URL만 주므로, `fileId`로는 저장한 뒤에 프론트가 삭제를 부를 수 없었다. 업로드 URL 발급 때 받은 `fileUrl`을 `encodeURIComponent`로 인코딩해 보낸다. 이 서버가 발급한 URL이 아니거나 이미 지워졌으면 404 `FILE_NOT_FOUND`, 남이 올린 파일이면 403 `NO_PERMISSION`, `fileUrl`이 없으면 400 `VALIDATION_FAILED`다. 연습 녹음 상세의 `audioUrl`은 임시 URL이라 이 API로 지울 수 없다(404). 응답 `id`는 지운 파일 id(발급 응답의 `fileId`)다.
> ‡ 로컬 저장에서는 발급된 `uploadUrl`이 S3가 아니라 이 서버의 `PUT /v1/files/local-upload/{fileKey}`를 가리킨다. 서빙(`GET /v1/files/local/**`)은 프로필·커버·포스터가 공개 정보라 인증 없이 열고(`PublicEndpoints` 등록), 원시 바이트를 그대로 내려줘야 해서 `api-conventions.md` §1의 "모든 응답은 `ApiResponse`로 감싼다" 규칙의 예외다.
> 초안에는 `PUT {uploadUrl}`(파일 업로드) 행도 있다. S3로 전환하면 그 말대로 **S3 주소에 직접 올리는 클라이언트 동작**이 되어 백엔드 엔드포인트가 아니게 된다 — 위 임시 엔드포인트가 그때 사라지는 이유다.

**업로드 PUT 규칙** — 발급받은 `uploadUrl`로 파일을 올릴 때의 규칙이다. 로컬과 S3가 **인증 헤더 하나만 다르다.**

| 항목 | 로컬 (지금) | S3 (전환 후) |
|---|---|---|
| `Authorization` 헤더 | **필수** (`Bearer {token}`). 업로더 본인만 올릴 수 있다 | **넣으면 안 된다.** presigned URL 서명과 충돌해 S3가 거부한다 |
| `Content-Type` 헤더 | 발급 때 보낸 `contentType`과 같아야 한다(대소문자 무시). 다르거나 없으면 415 `UNSUPPORTED_MEDIA_TYPE` | 같아야 한다. 다르면 S3가 403 |
| 바디 | 파일 바이트 그대로(raw). `FormData`(multipart)로 보내면 Content-Type이 달라 415. 비어 있으면 400 `EMPTY_FILE`, 도메인별 한도(이미지 10MB·연습 녹음 1.95MB) 초과면 400 `FILE_SIZE_EXCEEDED` | 파일 바이트 그대로(raw). **크기 한도가 걸리지 않는다** — 서버를 거치지 않고 S3에 바로 올라가기 때문이다(아래 ※) |
| 만료 | `expiresAt`(발급 후 10분)이 지나면 403 `UPLOAD_URL_EXPIRED` | 만료되면 S3가 403 |
| 재업로드 | 만료 전엔 같은 `uploadUrl`로 다시 올리면 덮어쓴다 | 같음 |

> ※ **도메인별 형식·크기**(2026-11-09, 이슈 #154): 이미지 3개 도메인은 `jpg` `jpeg` `png` `webp`·10MB, 연습 녹음 `PRACTICE_AUDIO`(폴더 `practice/`)는 `mp3` `m4a` `wav`·1.95MB(1,950,000바이트)다 — AI 연습 피드백 API가 녹음을 Base64 2,600,000자까지 받아서 그 원본 크기에 맞췄다(2026-11-11, 이전 100MB). 표 전체는 `dto-spec.md` `PresignedUrlResponse`. S3는 presigned PUT이 크기를 제한하지 않아 업로드 때 한도가 걸리지 않는다 — 연습 녹음은 분석 요청 때 서버가 실제 크기를 확인한다(`FileService.findSize`·`maxUploadBytes`). 업로드 자체를 막으려면 발급 요청에 `fileSize`를 받아 서명에 넣어야 해서 후속으로 논의한다.
>
> `PRACTICE_AUDIO`는 **비공개**다. `fileUrl`로 바로 열리지 않고, 다른 도메인이 `FileService.findOwnedFileKey`로 "내가 올린 그 도메인 파일인지" 확인해 key를 저장한 뒤 읽을 때마다 `FileService.issueReadUrl`로 임시 URL(S3 presigned GET)을 만든다. 로컬 저장에서는 임시 URL 대신 공개 서빙 경로가 나간다(개발용).

> ⚠️ **S3 전환 예정(조만간).** 전환해도 `PresignedUrlResponse` 구조(`fileId`·`uploadUrl`·`fileUrl`·`expiresAt`)는 그대로지만, **업로드 PUT에서 `Authorization` 헤더를 빼야 한다** — 프론트가 바꿔야 하는 유일한 지점이다. 지금부터 "`uploadUrl`이 이 서버의 `/v1/files/local-upload/`를 가리킬 때만 Bearer를 붙인다"로 분기해 두면 전환 때 프론트 코드 변경이 없다. 전환 시 위 로컬 임시 엔드포인트 2개는 제거된다.

### dashboard

2·3·4팀 Service를 모두 호출한다. **3팀 작업 중 마지막.**

| 메서드 | 경로 | 기능 | Request | Response | 비고 |
|---|---|---|---|---|---|
| GET | `/v1/dashboard` | 대시보드&nbsp;통합 | | `DashboardResponse` | 7.3 / 7.1.1 |

> 대시보드는 2026-11-04 구현했다(이슈 #135). 합주·공고 개수·기간 기준과 `recommendedSongs: null`(AI 연결 전)은 `dto-spec.md` DashboardResponse 참고.

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

## §12. 외부 서버 호출 — AI 서버 (곡 카탈로그 동기화 · 팀원 추천)

프론트가 부르는 API가 아니라 **백엔드가 AI 서버를 부르는** 호출이다(2026-11-06, 이슈 #147). AI 서버는 백엔드를 부르지 않는다 — 처음엔 AI 서버가 `PUT /v1/internal/songs`로 곡을 넣는 방향이었으나(PR #148) 방향을 바꿨다.

| 메서드 | AI 서버 경로 | 쿼리 | 응답 | 비고 |
|---|---|---|---|---|
| GET | `{AI_SERVER_BASE_URL}/v1/songs` 🚧 | `cursor`(첫 요청엔 없음) · `limit`(500) | `AiSongPage` | `Authorization: Bearer {AI_SERVER_API_KEY}` |

> 🚧 **잠정안** — AI 서버(`moyeorock-ai`)에 아직 HTTP 서버가 없어 경로·쿼리·응답 모양은 AI 팀과 맞추기 전이다. 바뀌면 `AiSongClient`·`AiSongPage`와 이 절을 함께 고친다.

- **언제**: 매일 04:00(Asia/Seoul) 스케줄. `AI_SONG_SYNC_ENABLED=true`일 때만 돈다(로컬·테스트 기본 꺼짐). `AI_SERVER_BASE_URL`이 비어 있으면 건너뛴다. 한 번에 하나만 돈다. `POST /v1/songs/sync`로 바로 돌릴 수도 있다(§song, 이슈 #162) — 스케줄 켜기와 무관하고, 스케줄과 겹치면 409다
- **어떻게**: `nextCursor`가 `null`이 될 때까지 받은 커서로 다음 페이지를 부른다. AI 서버 호출은 트랜잭션 밖에서 하고 **페이지마다 따로 커밋**한다 — 중간에 실패해도 앞 페이지는 남고, 다음 회차에 처음부터 다시 받는다. 같은 커서를 다시 돌려주면 무한히 돌지 않고 그 회차를 끝낸다
- **저장**: `externalId`(AI 쪽 곡 id, 대소문자 구분 — V13) 기준 upsert. 없으면 만들고, 있으면 받은 값으로 **통째로** 바꾼다(빠진 선택 값은 비워진다). 곡 `id`는 그대로다. **카탈로그에 없는 곡은 지우지 않는다** — 팀 선택곡·셋리스트가 참조한다
- **바꾼 곡 정보는 그 곡을 참조하는 곳에 그대로 보인다** — 팀 선택곡·셋리스트(끝난 공연 포함)·저장 목록은 `songs.id`만 들고 있어 제목·아티스트를 고치면 함께 바뀐다. 카탈로그 정정을 반영하려는 의도된 동작이다
- **잘못된 곡**: 필수 값 누락·길이 초과·`externalId` 앞뒤 공백·한 곡의 같은 파트 중복·한 페이지 안 `externalId` 중복은 **그 곡만 건너뛰고** 로그(`페이지·순번·externalId·사유`)를 남긴다. 없는 enum 값은 응답 전체를 읽지 못해 그 회차가 실패한다
- **값**: 우리 enum 그대로다 — 장르 `Genre` 11개(맞는 게 없으면 `ETC`), 파트 `Instrument`, 수준 `Level`. 필드 정의는 `dto-spec.md` AiSongPage

### 팀원 추천 호출 (team) — 2팀

프론트가 `GET /v1/teams/{id}/members/recommendations`를 부를 때마다 백엔드가 AI 서버를 **동기로** 부른다(2026-11-08, 이슈 #150). 곡 카탈로그와 달리 미리 받아 두는 게 아니다. AI 서버 주소·키는 곡 동기화와 같은 `AI_SERVER_BASE_URL`·`AI_SERVER_API_KEY`(`AiServerProperties`)를 쓴다.

| 메서드 | AI 서버 경로 | 요청 | 응답 | 비고 |
|---|---|---|---|---|
| POST | `{AI_SERVER_BASE_URL}/v1/recommendations/members` 🚧 | `AiRecommendRequest` | `AiRecommendResponse` | `Authorization: Bearer {AI_SERVER_API_KEY}` · 연결 3초·읽기 30초 |

> 🚧 **잠정안** — AI 팀과 맞추기 전이다. AI 팀이 우리 백엔드 스웨거에 맞춰 붙이기로 해서 요청·응답은 ERD 컬럼과 우리 enum 그대로 먼저 정했다. 바뀌면 `AiMemberRecommendationClient`·`AiRecommendRequest`·`AiRecommendResponse`와 이 절을 함께 고친다. 추천 모델의 입력 값이 ERD에 모두 있는지는 AI 팀 확인이 남았다.

- **누가 후보를 고르나**: 백엔드가 고른다. `UserService.findRecommendationCandidates`(1팀 소유 — 이슈 #150에서 2팀이 추가, 1팀 검토 필요)가 활동 중(`ACTIVE`)·추천 허용(`is_recommendable`)·요청 악기 보유·팀원 제외 조건에 맞는 사용자 중 **무작위로 최대 200명**을 고른다(2026-11-11, PR #151 리뷰 — 앞에서부터 자르면 늦게 가입한 사용자가 영영 후보가 되지 못한다). 조건에 맞는 사용자가 200명 이하면 전원을 보내고, 넘으면 호출할 때마다 후보가 달라져 결과도 조금씩 달라질 수 있다.
- **보내는 값**: 팀(`id`·`region`·`genres`), 찾는 `instrument`, 현재 팀원(`userId`·이 팀에서 맡은 `instrument`·그 악기의 `level`), 후보(`userId`·`region`·`genres`·요청 악기의 `level`). 온보딩 전 사용자는 `region`·`genres`가, 실력 정보가 없는 팀원은 `level`이 `null`이다. 필드 정의는 `dto-spec.md` AiRecommendRequest.
- **받는 값**: 후보별 `userId`·`score`·`reasons`. 백엔드는 우리가 보내지 않은 `userId`, 점수가 없는 항목, 같은 `userId`의 뒤쪽 항목, 탈퇴해 조회되지 않는 사용자를 버리고 점수 높은 순 최대 10명을 응답한다.
- **실패**: 4xx·5xx·타임아웃·`candidates` 누락·주소 미설정은 모두 `503 RECOMMENDATION_UNAVAILABLE`이다. 후보가 0명이면 AI를 부르지 않고 빈 `candidates`로 `200`이다.
- **트랜잭션**: 다른 도메인 Service 호출과 AI 응답 대기가 있어 `@Transactional`을 붙이지 않는다.

---

## §13. 연습 (practice) — 4팀

개인 연습 녹음을 올려 AI 분석을 받는다(2026-11-09 신규, 이슈 #155 · Notion `Backend (1) > API 명세서 > API 초안` 도메인 "연습" 2026-11-09 대조). 녹음과 분석 결과는 **올린 본인만** 본다 — 남의 녹음은 없는 녹음과 같게 404다.

| 메서드 | 경로 | 기능 | Request | Response |
|---|---|---|---|---|
| POST | `/v1/practice-recordings` | 분석&nbsp;요청 (**202**) | `PracticeRecordingCreateRequest` | `PracticeRecordingSummaryResponse` |
| GET | `/v1/users/me/practice-recordings?page=&size=` | 내&nbsp;연습&nbsp;녹음&nbsp;목록 | | `PageResponse<PracticeRecordingSummaryResponse>` |
| GET | `/v1/practice-recordings/{id}` | 상세&nbsp;(분석&nbsp;결과) | | `PracticeRecordingDetailResponse` |
| POST | `/v1/practice-recordings/{id}/retry` | 분석&nbsp;다시&nbsp;요청 (**202**) | | `PracticeRecordingSummaryResponse` |
| DELETE | `/v1/practice-recordings/{id}` | 삭제 | | — (`data: null`) |

**흐름** — ① 업로드 URL 발급(`domain: PRACTICE_AUDIO`) → 녹음 파일 PUT → ② 받은 `fileUrl`을 `audioUrl`로, 녹음한 세션을 `part`로, 연습한 곡(곡 검색 API의 id)을 `songId`로 분석 요청 → 202 `SUBMITTED` → ③ 서버가 AI에 보내면 `ANALYZING`, 끝나면 `DONE`(실패 `FAILED`) → ④ 프론트는 상세를 5초쯤 간격으로 조회하다 `DONE`·`FAILED`면 멈춘다. 진행률은 없다.

- **분석은 비동기다.** 요청이 커밋된 뒤 서버 안의 전용 실행기가 하나씩 처리한다 — 서버 전체에서 한 번에 하나, **한 사람당 진행 중(`SUBMITTED`·`ANALYZING`) 분석도 하나**다(다시 요청 포함)
- **실제 AI 연습 피드백 API로 분석한다**(`practice.ai.type=ai`. 값이 없으면 가짜 AI `fake` — 결과 앞에 "(테스트)"가 붙는다). 원곡은 AI 서버에 있어 곡의 `external_id`와 녹음 파일(Base64)만 보낸다(2026-11-11, AI 팀 핸드오프)
- **녹음은 3~60초, 파일은 1.95MB 이하**여야 한다. AI가 분석을 지원하는 곡은 시범 곡(30곡)뿐이다 — 지원하지 않는 곡, 원곡과 맞추지 못한 녹음(AI `review_required`), 길이·형식이 맞지 않는 녹음은 `FAILED`가 되고 `failureReason`에 사유가 온다. 곡이 지원되는지는 분석 요청 때 확인하지 않는다
- **피드백 한 줄 = AI가 찾은 "원곡과 다르게 연주한 후보 구간" 하나다.** AI가 세션·문장을 주지 않아 세션은 녹음한 세션, 내용은 `"원곡 0:20~0:23 구간과 다르게 연주했어요."` 고정 문장이다. 구간(초)은 시작은 내림·끝은 올림한 정수다
- **`audioUrl`(상세)은 조회할 때마다 새로 만드는 임시 URL**(S3 presigned GET, 30분)이다 — 녹음은 비공개라 업로드 때 받은 `fileUrl`로는 재생되지 않는다. 폴링 중에도 매번 달라지므로 재생 중인 `<audio>`의 `src`를 바꾸지 말고, 재생이 403으로 끊길 때만 다시 받는다
- **서버가 재시작(배포)하면 대기·분석 중이던 건은 `FAILED`**("서버 재시작으로 분석이 중단되었습니다. 다시 요청해 주세요.")가 된다 — 다시 요청하면 된다
- **분석 중에도 지울 수 있다.** 나중에 도착한 결과는 버린다. 녹음 파일(S3)은 지워지지 않는다 — 필요하면 업로드 때 받은 `fileUrl`로 `DELETE /v1/files?fileUrl=`(상세의 `audioUrl`은 임시 URL이라 지울 수 없다, 이슈 #164)
- **회원 탈퇴하면 연습 녹음·분석 결과·녹음 파일이 전부 지워진다**(§1 탈퇴, 2026-11-11)
- 다시 요청은 `FAILED`만 된다. 실패 사유는 비우고, `createdAt`(요청 시각)은 처음 그대로다
- **녹음은 혼자 연습한 세션 하나의 녹음이다.** 사용자가 녹음 파일을 올리고(서비스 안에서 녹음하지 않는다) 세션(`part`)을 고른다. 세션은 AI에 힌트로 넘기고 목록·상세에도 내려준다(2026-11-09 추가)

**에러**

| 코드 | 상태 | 상황 |
|---|---|---|
| `VALIDATION_FAILED` | 400 | 제목 빈칸·100자 초과, `audioUrl` 빈칸, `part` 없음·없는 세션 값, `songId` 없음 / `audioUrl`이 내가 올린 `PRACTICE_AUDIO` 파일이 아님·업로드가 끝나지 않음·1.95MB 초과 — `message`가 `"audioUrl: …"`로 사유를 준다 |
| `SONG_NOT_FOUND` | 404 | 없는 곡 (분석 요청) |
| `PRACTICE_RECORDING_NOT_FOUND` | 404 | 없는 녹음이거나 남의 녹음 (상세·다시 요청·삭제) |
| `INVALID_STATE` | 409 | 다시 요청인데 실패한 녹음이 아님 |
| `PRACTICE_ANALYSIS_IN_PROGRESS` | 409 | 진행 중인 내 녹음이 이미 있음 (분석 요청·다시 요청) |

> 노션 분석 요청에는 `songId`(필수)·404 `SONG_NOT_FOUND`와 400 칸의 "업로드가 끝나지 않음·1.95MB 초과"가 아직 없다(2026-11-11) — S3는 업로드(PUT) 때 크기를 막지 못해 분석 요청 때 서버가 확인하기로 했다(이슈 #154·#155). 노션 반영 필요.

---

## 알려진 공백 · 검토 필요

| # | 항목 | 내용 |
|---|---|---|
| 1 | DTO 필드 정의 | `docs/specs/dto-spec.md`에 정리 완료. `API 초안`에는 DTO 정보가 없어 기존 정의를 그대로 유지했다. 일부 DTO 페이지는 미작성 |
| 2 | 합주(rehearsal) 제외 | `API 초안`의 합주 행이 `v1/fee/...`(`studentFeeId`) 등 무관한 경로에 엔드포인트 누락까지 있어 이번 반영에서 제외했다(§3). 팀 정리 후 별도 반영 필요 |
| 3 | 총 개수 | 파일 도메인에 `DELETE /v1/files/{fileId}`가 추가되며 전체 개수가 바뀌었다. `docs/conventions/domains.md`·`docs/conventions/dto-naming.md`의 총계 갱신 필요 |
| 4 | performance 6 vs 7 | ✅ 해소(§8) — 원본 행 수는 7(셋리스트 확정 포함), `performance` 도메인 소유 개수는 6. **2026-10-30 갱신(이슈 #85)**: 공연 셋리스트 조회·넘기기가 생기고 셋리스트 3개를 performance가 받게 돼 `performance` 소유는 9. **2026-11-02**: 셋리스트 확정 해제가 더해져 10 |
| 5 | song 3 vs 2 | 여전히 미해소(§9) — `API 초안`도 song·setlist 소유를 구분하지 않는다. 2026-11-02: 추천 2개는 이슈 #103 범위에서 빼고 방식과 함께 따로 정한다 |
| 6 | 위치 불명 메모 | 원본의 "모임장" 단독 메모 2건은 인접 행(공연 생성·곡 검색)에 붙어 있었으나 대상 불확실 |
| 7 | 내보내기 PATCH | ✅ 해소 — 팀·모임 모두 `/status` 접미사로 확정, 노션도 2026-09-11 수정 반영(§2, §7) |
| 8 | 모임원 목록 응답 | `GroupMembersResponse` → `PageResponse<>` 개명 이력은 `API 초안`에 DTO 정보가 없어 갱신되지 않았다. 완성형은 `PageResponse<GroupMemberResponse>` (§7 §) |
| 9 | 경로 단수형 예외 | `recruit-post` 하나가 `API 초안`을 신뢰해 단수형 그대로 반영된 예외다(2026-09-11 팀 확인, 2026-11-03 현행 유지). `join-request`는 초안이 복수형으로 바뀌어 `join-requests`로 맞췄다. 노션의 공고 행은 아직 복수형이라 노션 정리 필요 |
| 10 | 파일 삭제 신규 | `DELETE /v1/files/{fileId}`가 `API 초안`에서 새로 확인됨(§10). DTO·구현 담당 확인 필요 |
| 11 | 공고 세션별 마감 | 공고 `status`는 공고 전체에 하나뿐이라 세션별(예: BASS만 마감) 마감이 불가능하고, `wanted_slots`(instrument + count)에도 세션별 마감·충원 상태를 담을 자리가 없다. 세션별 현황(`filledCount`)은 `join_requests`의 `recruit_post_id` + `instrument` + `status=APPROVED` 집계로 스키마 변경 없이 가능하나, 세션별 마감 상태는 `wanted_slots` JSON 구조 변경이라 ERD 변경 절차(절대 규칙 5)가 필요하다. 화면 기획 확인 후 join 도메인 착수 시 함께 결정 → **2026-11-03 join 구현에서 세션별 마감 없이 가기로 했다**(정원을 검사하지 않고 팀장이 승인 때 판단, `appliedCount`는 세션별 대기 수만 보여줌). 같은 뿌리로 마감된 공고의 수정(`PUT`)은 `409 INVALID_STATE`로 막았다(2026-10-08 결정). 세션별 마감을 도입하면 세션 단위로 다시 정한다 |
| 12 | 내 팀 목록 신규 | ✅ 확정(2026-10-31) — 노션 `API 초안`에 `[추가 필요]`로 올라온 `GET /v1/users/me/teams`(§2 ‡)를 2팀 소유로 확정하고 `docs/conventions/domains.md`·`docs/conventions/dto-naming.md` 총계(73 → 74)에 반영했다. 노션 쪽은 제목의 `[추가 필요]`와 담당자·진행 상태 정리가 남아 있다 |
| 13 | 공연 목록 `status` 필터 | ✅ 결정(2026-11-02, PR #84 리뷰) — 쿼리 이름을 `period`로 바꾸고 공연 상태로 묶는다(`UPCOMING`=`PLANNED`·`RECRUITING`, `PAST`=`DONE`·`CANCELED`, §8 ※). 응답 `status`는 실제 상태. 노션 정정 완료(2026-11-02). 원래 내용: `API 초안` 모임 공연 목록의 쿼리 `status`와 응답 `status` 예시가 `UPCOMING`·`PAST`인데 `performances.status`(`PLANNED|RECRUITING|DONE|CANCELED`)에 없는 값이다. 다가올·지난 공연을 나누는 기준(공연일 vs 상태)부터 정해야 해서 필터는 반영하지 않았었다 |
| 14 | 공연 상태 값 | ✅ 결정(2026-11-01, 이슈 #88) — ERD 값 그대로 받고 응답은 `{id, status}`, 403은 `NOT_GROUP_OWNER`(§8 ⊕). 노션 정정 필요. 원래 내용: `API 초안` 공연 상태 변경의 값 `PLANNED`·`ONGOING`·`ENDED`·`CANCELLED`가 ERD(`PLANNED|RECRUITING|DONE|CANCELED`)와 다르고, 응답에 `updatedAt`, 403 코드가 `NOT_GROUP_ADMIN`(모임 코드는 `NOT_GROUP_OWNER`)이었다 |

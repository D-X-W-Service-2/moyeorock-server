# API 명세 — 전 도메인 통합

- **원본**: Notion `API 명세서 v0` (2026-08-27 노션 실시간 대조 완료 — 8/22 노션 변경분 반영)
- **절 번호**: 이 파일과 `docs/specs/dto-spec.md`에서만 쓰는 좌표다
- **인증**: 모든 API는 유저 인증 필요. 예외(auth 3개)는 §11에 별도 표기
- **DTO 필드 정의**: `docs/specs/dto-spec.md` 참조. `*` 표시 Response는 원본 표에 비어 있으나 DTO 페이지의 이름·태그로 매칭한 것이다

---

## §1. 사용자 (user) — 1팀

| 메서드 | 경로 | 기능 | Request | Response |
|---|---|---|---|---|
| GET | `/v0/users/me` | 내&nbsp;프로필&nbsp;조회 | | `UserMeResponse` |
| PUT | `/v0/users/me` | 프로필&nbsp;설정&nbsp;/&nbsp;수정 | `UserUpdateRequest` | |
| DELETE | `/v0/users/me` | 탈퇴 | | `UserWithdrawResponse` * |
| POST | `/v0/users/me/onboarding` | 온보딩&nbsp;등록 | `OnboardingCreateRequest` | |
| PUT | `/v0/users/me/instruments` | 세션/실력&nbsp;수정 | `UserInstrumentUpdateRequest` | |
| GET | `/v0/users/me/activities` | 내&nbsp;활동 | | `UserActivityResponse` |
| GET | `/v0/users/{id}` | 타인&nbsp;프로필&nbsp;조회 | | `UserProfileResponse` |
| GET | `/v0/users/search?nickname=` | 사용자&nbsp;검색 | | `PageResponse<UserSummaryResponse>` |
| GET | `/v0/users/nickname/check` | 닉네임&nbsp;중복&nbsp;확인 | | `NicknameCheckResponse` † |

> † 원본 Notion 표에는 없고 `docs/conventions/dto-naming.md` §2가 정의한 9번째 엔드포인트다. Notion 명세에 역반영 필요 (2026-08-27 기준 여전히 미반영).
> `/v0/users/search`는 원본에서 팀 섹션에 있었지만 user 도메인 소유다.
> `PUT /v0/users/me/instruments`의 응답이 `UserInstrumentsResponse`다 (`docs/conventions/dto-naming.md` §2).

---

## §2. 팀 (team) — 2팀

| 메서드 | 경로 | 기능 | Request | Response |
|---|---|---|---|---|
| POST | `/v0/teams` | 팀&nbsp;생성 | `TeamCreateRequest` | |
| GET | `/v0/teams` ¶ | 팀&nbsp;목록 | | `PageResponse<TeamsummaryResponse>` |
| GET | `/v0/teams/{id}` | 팀&nbsp;상세 | | `TeamDetailResponse` |
| PUT | `/v0/teams/{id}` | 팀&nbsp;정보&nbsp;수정 | `TeamUpdateRequest` | |
| PATCH | `/v0/teams/{id}/status` | 팀&nbsp;해체 | `TeamStatusUpdateRequest` | `TeamStatusResponse` * |
| GET | `/v0/teams/{id}/members` | 팀원&nbsp;목록 | | `TeamMembersResponse` |
| PATCH | `/v0/teams/{teamId}/members/{userId}` | 팀원&nbsp;역할&nbsp;변경 | `TeamMemberUpdateRequest` | |
| PATCH | `/v0/teams/{teamId}/members/{userId}` | 팀원&nbsp;내보내기·탈퇴&nbsp;‡ | | `TeamMemberRemoveResponse` * |
| GET | `/v0/teams/{id}/members/recommendations?instrument=` | 팀원&nbsp;추천&nbsp;(AI) | | `TeamMemberRecommendationResponse` |

> ¶ 팀 목록의 쿼리 파라미터: `region` · `genre` · `status` (전부 선택).
> ‡ 원본이 2026-08-22에 DELETE → **PATCH**로 변경됨 (행 재생성, Request/Response 비어 있음). 세팅 문서 기준 완성형은 경로에 `/status`를 붙이고 `TeamMemberStatusUpdateRequest`/`TeamMemberStatusResponse`를 쓰는 것 (`docs/conventions/api-conventions.md` §5, `docs/conventions/dto-naming.md` §3) — 노션은 메서드만 바뀐 반영 미완 상태라 이대로면 "팀원 역할 변경"과 메서드+경로가 충돌한다. 노션 보완 건의 예정.
> `TeamsummaryResponse`는 원본 표기 그대로다. 컨벤션대로면 `TeamSummaryResponse` — 구현 시 교정하고 원본에 알릴 것.

---

## §3. 합주 (rehearsal) — 2팀

| 메서드 | 경로 | 기능 | Request | Response |
|---|---|---|---|---|
| GET | `/v0/teams/{teamId}/rehearsals?from=&to=` | 팀&nbsp;합주&nbsp;일정&nbsp;목록 | | `RehearsalsResponse` |
| POST | `/v0/teams/{teamId}/rehearsals` | 합주&nbsp;일정&nbsp;생성 | `RehearsalCreateRequest` | |
| GET | `/v0/rehearsals/{id}` | 합주&nbsp;일정&nbsp;상세 | | `RehearsalDetailResponse` |
| PUT | `/v0/rehearsals/{id}` | 합주&nbsp;일정&nbsp;수정 | `RehearsalUpdateRequest` | |
| DELETE | `/v0/rehearsals/{id}` | 합주&nbsp;일정&nbsp;삭제 | | |
| GET | `/v0/users/me/rehearsals?from=&to=` | 내&nbsp;합주&nbsp;일정 | | `RehearsalsResponse` |

---

## §4. 모집 공고 (recruit) — 3팀

| 메서드 | 경로 | 기능 | Request | Response |
|---|---|---|---|---|
| POST | `/v0/recruit-posts` | 공고&nbsp;작성 | `RecruitPostCreateRequest` | |
| GET | `/v0/recruit-posts` ¶ | 공고&nbsp;목록 | | `PageResponse<RecruitPostSummaryResponse>` |
| GET | `/v0/recruit-posts/{id}` | 공고&nbsp;상세 | | `RecruitPostDetailResponse` |
| PUT | `/v0/recruit-posts/{id}` | 공고&nbsp;수정 | `RecruitPostUpdateRequest` | |
| PATCH | `/v0/recruit-posts/{id}/status` | 공고&nbsp;마감·삭제 | `RecruitPostStatusUpdateRequest` | `RecruitPostStatusResponse` * |

> ¶ 공고 목록의 쿼리 파라미터: `targetType` · `region` · `instrument` · `status` · `authorId` (전부 선택).
> ⚠️ 원본은 마감·삭제의 Request를 `RecruitPostStatusResponse`로 적었다 (2026-08-27 기준 여전히 그대로). DTO 목록에 `RecruitPostStatusUpdateRequest`가 따로 있으므로 **원본의 오기로 판단**해 교정했다.

---

## §5. 가입 신청 (join · JoinRequestController) — 3팀

팀 가입, 모임 가입, 팀 제안 **전부 이걸로 처리**한다. 초대(§6)와 엔티티·테이블(`join_requests`)을 공유하고 `direction=APPLY`로 구분한다.

| 메서드 | 경로 | 기능 | Request | Response | 비고 |
|---|---|---|---|---|---|
| POST | `/v0/join-requests` | 가입&nbsp;신청 | `JoinRequestCreateRequest` | | |
| GET | `/v0/join-requests` ¶ | 받은&nbsp;신청&nbsp;목록 | | `PageResponse<JoinRequestResponse>` | 모임장/팀장 |
| GET | `/v0/users/me/join-requests?status=` | 내&nbsp;신청&nbsp;목록 | | `PageResponse<JoinRequestResponse>` | |
| PATCH | `/v0/join-requests/{id}/approve` | 신청&nbsp;승인 | | `JoinApprovedResponse` * | 모임장/팀장 |
| PATCH | `/v0/join-requests/{id}/reject` | 신청&nbsp;거절 | | `JoinDecisionResponse` * | 모임장/팀장 |
| PATCH | `/v0/join-requests/{id}/cancel` | 신청&nbsp;취소 | | `JoinDecisionResponse` * | 신청자 본인 |

> ¶ 받은 신청 목록의 쿼리 파라미터: `targetType` · `targetId` · `status` (전부 선택).

---

## §6. 초대 (join · InvitationController) — 3팀

`direction=INVITE`. 별도 엔티티·테이블을 만들지 않는다 (`docs/conventions/domains.md` 참조).

| 메서드 | 경로 | 기능 | Request | Response |
|---|---|---|---|---|
| POST | `/v0/invitations` | 초대&nbsp;보내기 | `InvitationCreateRequest` | |
| GET | `/v0/invitations` ¶ | 보낸&nbsp;초대&nbsp;목록 | | `PageResponse<InvitationResponse>` |
| GET | `/v0/users/me/invitations?status=` | 받은&nbsp;초대&nbsp;목록 | | `PageResponse<InvitationResponse>` |
| PATCH | `/v0/invitations/{id}/accept` | 초대&nbsp;수락 | | `JoinApprovedResponse` * |
| PATCH | `/v0/invitations/{id}/decline` | 초대&nbsp;거절 | | `JoinDecisionResponse` * |
| PATCH | `/v0/invitations/{id}/cancel` | 초대&nbsp;취소 | | `JoinDecisionResponse` * |

> ¶ 보낸 초대 목록의 쿼리 파라미터: `targetType` · `targetId` · `status` (전부 선택).

---

## §7. 모임 (group) · 공지 (notice) — 4팀

### group

| 메서드 | 경로 | 기능 | Request | Response | 비고 |
|---|---|---|---|---|---|
| POST | `/v0/groups` | 모임&nbsp;생성 | `GroupCreateRequest` | | |
| GET | `/v0/groups/{id}` | 모임&nbsp;상세&nbsp;(동아리&nbsp;홈) | | `GroupDetailResponse` | |
| PUT | `/v0/groups/{id}` | 모임&nbsp;정보&nbsp;수정 | `GroupUpdateRequest` | | |
| GET | `/v0/groups/{id}/members` | 모임원&nbsp;목록 | | `PageResponse<>` (구 `GroupMembersResponse`) § | |
| PATCH | `/v0/groups/{groupId}/members/{userId}` | 모임원&nbsp;역할&nbsp;변경&nbsp;(모임장&nbsp;위임) | `GroupMemberUpdateRequest` | | 모임장 |
| PATCH | `/v0/groups/{groupId}/members/{userId}` | 모임원&nbsp;내보내기·탈퇴&nbsp;‡ | | `GroupMemberRemoveResponse` * | |

> ‡ 원본이 2026-08-22에 DELETE → **PATCH**로 변경됨. 세팅 문서 기준 완성형은 경로에 `/status`를 붙이고 `GroupMemberStatusUpdateRequest`/`GroupMemberStatusResponse`를 쓰는 것 (`docs/conventions/dto-naming.md` §8) — 노션은 반영 미완 상태라 "모임원 역할 변경"과 메서드+경로가 충돌한다. 노션 보완 건의 예정.
> § 원본 DTO 페이지 `GroupMembersResponse`가 2026-08-22에 `PageResponse<>`로 개명됨 (내용은 단일 멤버 JSON 그대로, 제네릭 미기입). 세팅 문서 기준 완성형은 `PageResponse<GroupMemberResponse>` (`docs/conventions/dto-naming.md` §8). 필드는 `docs/specs/dto-spec.md` §7 참조.

### notice

엔티티는 `GroupNotice`, 테이블은 `group_notices`. `Post`라는 이름을 쓰지 않는다.

| 메서드 | 경로 | 기능 | Request | Response | 비고 |
|---|---|---|---|---|---|
| POST | `/v0/groups/{id}/notices` | 공지&nbsp;작성 | `NoticeCreateRequest` | | 모임장 |
| GET | `/v0/groups/{id}/notices` | 공지&nbsp;목록 | | `PageResponse<NoticeResponse>` | 고정글(`is_pinned`) 우선 정렬 * |
| PUT | `/v0/notices/{id}` | 공지&nbsp;수정 | `NoticeUpdateRequest` | | |
| DELETE | `/v0/notices/{id}` | 공지&nbsp;삭제 | | | |

> \* "고정글 우선 정렬" · "is_pinned 토글" 메모는 원본에서 알림·북마크 행에 붙어 있었으나 문맥상 공지 기능으로 판단해 옮겼다. 팀 확인 필요.

---

## §8. 공연 (performance) — 4팀

| 메서드 | 경로 | 기능 | Request | Response | 비고 |
|---|---|---|---|---|---|
| POST | `/v0/groups/{groupId}/performances` | 공연&nbsp;생성 | `PerformanceCreateRequest` | | 모임장 |
| GET | `/v0/groups/{groupId}/performances` | 모임&nbsp;공연&nbsp;목록 | | `PageResponse<PerformanceSummaryResponse>` | |
| GET | `/v0/performances/{id}` | 공연&nbsp;상세 | | `PerformanceDetailResponse` | |
| PUT | `/v0/performances/{id}` | 공연&nbsp;정보&nbsp;수정 | `PerformanceUpdateRequest` | | 7.4 공지 + 공연 포스터 |
| PATCH | `/v0/performances/{id}/status` | 공연&nbsp;상태&nbsp;변경 | `PerformanceStatusUpdateRequest` | `PerformanceStatusResponse` * | `DONE` 시 소속 팀 해체 → 2팀 `TeamService` 호출 |
| POST | `/v0/performances/{performanceId}/teams` | 공연&nbsp;내&nbsp;팀&nbsp;생성 | `PerformanceTeamCreateRequest` | TeamDetailResponse 형태 * | 7.1 생성자 = OWNER · 2팀 `TeamService` 호출 |

> ⚠️ **원본 CSV에는 6개만 있다** (`docs/conventions/domains.md`는 performance 7개). `PUT /v0/performances/{id}/setlist`를 performance로 세면 7이 맞지만, `docs/conventions/domains.md`는 이를 **setlist 도메인 소유**로 명시한다(§9에 배치). 개수 기준을 어느 쪽으로 볼지 팀 확인 필요.

---

## §9. 노래 (song) · 셋리스트 (setlist) — 4팀

### song

| 메서드 | 경로 | 기능 | Request | Response | 비고 |
|---|---|---|---|---|---|
| GET | `/v0/songs?keyword=&genre=` | 곡&nbsp;검색 | | `PageResponse<SongResponse>` | 세션별 난이도 포함 |
| GET | `/v0/users/me/songs/recommendations` | 내&nbsp;취향&nbsp;추천곡 | | `SongRecommendationResponse` | 6.2.1 세션별 실력·장르 반영 |
| GET | `/v0/teams/{id}/songs/recommendations` | 팀&nbsp;셋리스트&nbsp;추천&nbsp;(AI) | | `TeamSongRecommendationResponse` | 7.2 |

> ⚠️ `docs/conventions/domains.md`는 song 2개로 집계한다. 팀 셋리스트 추천을 song에 두면 3개가 되므로 **소유 도메인(song vs setlist) 확인 필요.**

### setlist

`team_songs` 테이블 소유. 아래 2개가 전부다 — performance는 `team_songs`를 직접 건드리지 않는다.

| 메서드 | 경로 | 기능 | Request | Response | 비고 |
|---|---|---|---|---|---|
| PUT | `/v0/teams/{id}/setlist` | 팀&nbsp;셋리스트&nbsp;저장 | `TeamSetlistUpdateRequest` | `TeamSetlistResponse` * | 7.2 추천 결과에서 선택 |
| PUT | `/v0/performances/{id}/setlist` | 공연&nbsp;셋리스트&nbsp;확정 | `PerformanceSetlistUpdateRequest` | `PerformanceSetlistResponse` * | 진행도(progress)·순서 관리 |

---

## §10. 알림 (notification) · 북마크 (bookmark) · 대시보드 (dashboard) · 파일 (file) — 3팀

### notification

발생은 2·3·4팀 도메인에서 **이벤트로만** 전달받는다 (`NotificationService` 직접 호출 금지).

| 메서드 | 경로 | 기능 | Request | Response |
|---|---|---|---|---|
| GET | `/v0/notifications?unreadOnly=` | 알림&nbsp;목록 | | `NotificationsResponse` |
| PATCH | `/v0/notifications/read` | 알림&nbsp;읽음 | `NotificationReadRequest` | `NotificationReadResponse` * |

### bookmark

| 메서드 | 경로 | 기능 | Request | Response |
|---|---|---|---|---|
| POST | `/v0/bookmarks` | 저장&nbsp;추가 | `BookmarkCreateRequest` | |
| GET | `/v0/bookmarks?targetType=` | 저장&nbsp;목록 | | `PageResponse<BookmarkResponse>` |
| DELETE | `/v0/bookmarks/{id}` | 저장&nbsp;삭제 | | |

### file (`global/file` — 도메인 아님)

1팀(프로필)·4팀(커버·포스터)이 쓰므로 **최우선 착수 대상.**

| 메서드 | 경로 | 기능 | Request | Response |
|---|---|---|---|---|
| POST | `/v0/files/presigned-url` | 업로드&nbsp;URL&nbsp;발급 | `PresignedUrlCreateRequest` | `PresignedUrlResponse` |

### dashboard

2·3·4팀 Service를 모두 호출한다. **3팀 작업 중 마지막.**

| 메서드 | 경로 | 기능 | Request | Response | 비고 |
|---|---|---|---|---|---|
| GET | `/v0/dashboard` | 대시보드&nbsp;통합 | | `DashboardResponse` | 7.3 / 7.1.1 |

---

## §11. 로그인 (auth) — 1팀

인증이 필요 없는 유일한 그룹. 응답 코드는 원본 기재 그대로.

| 메서드 | 경로 | 기능 | Request | Response | 응답 코드 | 비고 |
|---|---|---|---|---|---|---|
| POST | `/v0/auth/kakao` | 카카오&nbsp;회원가입/로그인 | `UserOAuth2CodeRequest` | `AuthTokenResponse` † | 200, 400 | 이메일 회원과 중복 X |
| POST | `/v0/auth/signup` | 이메일&nbsp;회원가입 | `UserSignupRequest` | `AuthTokenResponse` † | 201, 400, 409 | 카카오 회원과 중복 X |
| POST | `/v0/auth/login` | 이메일&nbsp;로그인 | `UserLoginRequest` | `AuthTokenResponse` † | 200, 400, 401 | |

> † 원본 표에는 Response가 없다. `docs/conventions/dto-naming.md` §1 정의 — `AuthTokenResponse(accessToken, userId, nickname, onboardingCompleted)`를 3곳 공유, 리프레시 토큰 없음.

---

## 알려진 공백 · 검토 필요

| # | 항목 | 내용 |
|---|---|---|
| 1 | DTO 필드 정의 | `docs/specs/dto-spec.md`에 정리 완료. 공용 요약 객체는 확정·제안까지 완료 (dto-spec.md 상단), 일부 DTO 페이지는 미작성 |
| 2 | 총 개수 71 vs 72 | `docs/conventions/domains.md`는 72개. `GET /v0/users/me/instruments` 누락 추정 (§1, DTO 정의는 존재) |
| 3 | performance 6 vs 7 | 공연 셋리스트 확정의 소유 도메인에 따라 달라짐 (§8) |
| 4 | song 3 vs 2 | 팀 셋리스트 추천의 소유 도메인 확인 필요 (§9) |
| 5 | 위치 불명 메모 | 원본의 "모임장" 단독 메모 2건은 인접 행(공연 생성·곡 검색)에 붙어 있었으나 대상 불확실 |
| 6 | 내보내기 PATCH 미완 | 팀원·모임원 내보내기가 8/22에 DELETE → PATCH로 바뀌었으나 `/status` 경로·DTO 누락 (§2 ‡, §7 ‡) — 노션 보완 건의 예정 |
| 7 | 모임원 목록 응답 | `GroupMembersResponse` → `PageResponse<>` 개명(8/22), 완성형은 `PageResponse<GroupMemberResponse>` (§7 §) — 노션 보완 건의 예정 |
| 8 | Notion 역반영 대기 | `GET /v0/users/nickname/check` 추가 · 공고 마감·삭제 Request 오기(`RecruitPostStatusResponse` → `RecruitPostStatusUpdateRequest`) 교정 |

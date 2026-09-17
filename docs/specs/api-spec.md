# API 명세 — 전 도메인 통합

- **원본**: Notion `API 명세서 수정안` 페이지의 `API 초안` 데이터베이스 (2026-09-11 대조 완료)
- **버전**: 전 도메인 `/v1/`로 통일했다. **단, 합주(rehearsal, §3)는 초안 쪽 데이터가 아직 정리되지 않아 이번 반영에서 제외 — 기존 `/v0/` 그대로 유지**한다. 초안이 정리되면 별도로 반영한다.
- **절 번호**: 이 파일과 `docs/specs/dto-spec.md`에서만 쓰는 좌표다
- **인증**: 모든 API는 유저 인증 필요. 예외(auth 3개)는 §11에 별도 표기
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
| POST | `/v1/teams` | 팀&nbsp;생성 | `TeamCreateRequest` | |
| GET | `/v1/teams` ¶ | 팀&nbsp;목록 | | `PageResponse<TeamsummaryResponse>` |
| GET | `/v1/teams/{id}` | 팀&nbsp;상세 | | `TeamDetailResponse` |
| PUT | `/v1/teams/{id}` | 팀&nbsp;정보&nbsp;수정 | `TeamUpdateRequest` | |
| PATCH | `/v1/teams/{id}/status` | 팀&nbsp;해체 | `TeamStatusUpdateRequest` | `TeamStatusResponse` * |
| GET | `/v1/teams/{id}/members` | 팀원&nbsp;목록 | | `TeamMembersResponse` |
| PATCH | `/v1/teams/{teamId}/members/{userId}` | 팀원&nbsp;역할&nbsp;변경 | `TeamMemberUpdateRequest` | |
| PATCH | `/v1/teams/{teamId}/members/{userId}/status` | 팀원&nbsp;내보내기·탈퇴 | `TeamMemberStatusUpdateRequest` | `TeamMemberStatusResponse` * |
| GET | `/v1/teams/{id}/members/recommendations?instrument=` | 팀원&nbsp;추천&nbsp;(AI) | | `TeamMemberRecommendationResponse` |

> ¶ 팀 목록의 쿼리 파라미터: `region` · `genre` · `status` (전부 선택).
> 팀원 내보내기·탈퇴는 `API 초안`에서도 `/status` 접미사가 붙은 완성형으로 확인됐다(2026-09-11) — 과거 DELETE→PATCH 전환 미완 문제는 해소됐다.
> `TeamsummaryResponse`는 원본 표기 그대로다. 컨벤션대로면 `TeamSummaryResponse` — 구현 시 교정하고 원본에 알릴 것.

---

## §3. 합주 (rehearsal) — 2팀

**이번 업데이트에서 제외.** `API 초안`의 합주 도메인 행은 `v1/fee/...` · `{studentFeeId}` 등 무관한 경로로 되어 있고 일부는 엔드포인트가 비어 있어(2026-09-11 확인), 팀 정리 전까지는 기존 `/v0/` 명세를 그대로 유지한다.

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
| POST | `/v1/recruit-post` | 공고&nbsp;작성 | `RecruitPostCreateRequest` | |
| GET | `/v1/recruit-post` ¶ | 공고&nbsp;목록 | | `PageResponse<RecruitPostSummaryResponse>` |
| GET | `/v1/recruit-post/{id}` | 공고&nbsp;상세 | | `RecruitPostDetailResponse` |
| PUT | `/v1/recruit-post/{id}` | 공고&nbsp;수정 | `RecruitPostUpdateRequest` | |
| PATCH | `/v1/recruit-post/{id}/status` | 공고&nbsp;마감·삭제 | `RecruitPostStatusUpdateRequest` | `RecruitPostStatusResponse` * |

> ¶ 공고 목록의 쿼리 파라미터: `targetType` · `region` · `instrument` · `status` · `authorId` (전부 선택).
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
| GET | `/v1/groups/{id}` | 모임&nbsp;상세&nbsp;(동아리&nbsp;홈) | | `GroupDetailResponse` | |
| PUT | `/v1/groups/{id}` | 모임&nbsp;정보&nbsp;수정 | `GroupUpdateRequest` | | |
| GET | `/v1/groups/{id}/members` | 모임원&nbsp;목록 | | `PageResponse<>` (구 `GroupMembersResponse`) § | |
| PATCH | `/v1/groups/{groupId}/members/{userId}` | 모임원&nbsp;역할&nbsp;변경&nbsp;(모임장&nbsp;위임) | `GroupMemberUpdateRequest` | | 모임장 |
| PATCH | `/v1/groups/{groupId}/members/{userId}/status` | 모임원&nbsp;내보내기·탈퇴 | `GroupMemberStatusUpdateRequest` | `GroupMemberStatusResponse` * | |

> `API 초안`에서도 원래 역할 변경과 같은 경로(`/members/{userId}`)를 공유하고 있었으나, `docs/conventions/api-conventions.md` §5 규칙(상태 전이는 `/status`)에 맞춰 내보내기·탈퇴 경로에 `/status`를 붙이고 노션도 함께 수정했다(2026-09-11). 과거 "DELETE → PATCH 전환 미완" 문제는 해소됐다.
> § 원본 DTO 페이지 `GroupMembersResponse`가 `PageResponse<>`로 개명된 이력이 있다(2026-08-22). 완성형은 `PageResponse<GroupMemberResponse>` (`docs/conventions/dto-naming.md` §8). 필드는 `docs/specs/dto-spec.md` §7 참조. `API 초안`에는 DTO 정보가 없어 이 이력은 갱신되지 않았다.

### notice

엔티티는 `GroupNotice`, 테이블은 `group_notices`. `Post`라는 이름을 쓰지 않는다.

| 메서드 | 경로 | 기능 | Request | Response | 비고 |
|---|---|---|---|---|---|
| POST | `/v1/groups/{id}/notices` | 공지&nbsp;작성 | `NoticeCreateRequest` | | 모임장 |
| GET | `/v1/groups/{id}/notices` | 공지&nbsp;목록 | | `PageResponse<NoticeSummaryResponse>` | 고정글(`is_pinned`) 우선 정렬 * |
| GET | `/v1/notices/{id}` | 공지&nbsp;상세 | | `NoticeDetailResponse` | ‖ |
| PUT | `/v1/notices/{id}` | 공지&nbsp;수정 | `NoticeUpdateRequest` | | |
| DELETE | `/v1/notices/{id}` | 공지&nbsp;삭제 | | | |

> \* "고정글 우선 정렬" · "is_pinned 토글" 메모는 원본에서 알림·북마크 행에 붙어 있었으나 문맥상 공지 기능으로 판단해 옮겼다. 팀 확인 필요.
> ‖ `API 초안`에도 이 엔드포인트는 없다. **팀 결정으로 목록·상세를 분리해 신규 추가한 엔드포인트**로, 기존 문서의 결정을 그대로 유지한다 — 목록은 `body`를 뺀 `NoticeSummaryResponse`, 상세는 전체 필드를 담는 `NoticeDetailResponse`다(`docs/specs/dto-spec.md` §7).
> `API 초안`은 공지 수정·삭제를 `/groups/{id}/notices/{id}`(그룹 하위 경로)로 적었으나, 기존 컨벤션(단독 경로 `/notices/{id}`)을 유지하기로 하고 노션도 함께 수정했다(2026-09-11). 생성·목록은 그대로 `/groups/{id}/notices` 아래에 둔다.

---

## §8. 공연 (performance) — 4팀

| 메서드 | 경로 | 기능 | Request | Response | 비고 |
|---|---|---|---|---|---|
| POST | `/v1/groups/{groupId}/performances` | 공연&nbsp;생성 | `PerformanceCreateRequest` | | 모임장 |
| GET | `/v1/groups/{groupId}/performances` | 모임&nbsp;공연&nbsp;목록 | | `PageResponse<PerformanceSummaryResponse>` | |
| GET | `/v1/performances/{id}` | 공연&nbsp;상세 | | `PerformanceDetailResponse` | |
| PUT | `/v1/performances/{id}` | 공연&nbsp;정보&nbsp;수정 | `PerformanceUpdateRequest` | | 7.4 공지 + 공연 포스터 |
| PATCH | `/v1/performances/{id}/status` | 공연&nbsp;상태&nbsp;변경 | `PerformanceStatusUpdateRequest` | `PerformanceStatusResponse` * | `DONE` 시 소속 팀 해체 → 2팀 `TeamService` 호출 |
| POST | `/v1/performances/{performanceId}/teams` | 공연&nbsp;내&nbsp;팀&nbsp;생성 | `PerformanceTeamCreateRequest` | TeamDetailResponse 형태 * | 7.1 생성자 = OWNER · 2팀 `TeamService` 호출 |

> ✅ **개수 논쟁 해소(2026-09-11)**: `API 초안`은 이 표의 6개에 `PUT /v1/performances/{id}/setlist`(공연 셋리스트 확정)까지 더해 7개 행을 공연 도메인 아래 두었다. 다만 소유는 여전히 `docs/conventions/domains.md`가 정한 대로 **setlist 도메인**이다(§9에 배치) — `team_songs` 테이블의 `UNIQUE` 제약을 한 서비스가 검증해야 하기 때문이다. 즉 "공연" 라벨의 원본 행 수는 7, `performance` 도메인이 실제 소유하는 엔드포인트 수는 6이다.
> `API 초안`의 이 7개 행은 반영 당시(2026-09-10 이전) `인증 여부`·`진행 상태`가 비어 있었다. 모든 API는 인증이 필요하다는 원칙(상단 참고)에 따라 2026-09-11에 전부 "인증 필요"로 채우고 노션도 함께 수정했다.

---

## §9. 노래 (song) · 셋리스트 (setlist) — 4팀

### song

| 메서드 | 경로 | 기능 | Request | Response | 비고 |
|---|---|---|---|---|---|
| GET | `/v1/songs?keyword=&genre=&page=&size=` ¶ | 곡&nbsp;검색 | | `PageResponse<SongResponse>` | 세션별 난이도 포함 |
| GET | `/v1/users/me/songs/recommendations` | 내&nbsp;취향&nbsp;추천곡 | | `SongRecommendationResponse` | 6.2.1 세션별 실력·장르 반영 |
| GET | `/v1/teams/{id}/songs/recommendations` | 팀&nbsp;셋리스트&nbsp;추천&nbsp;(AI) | | `TeamSongRecommendationResponse` | 7.2 |

> ¶ `API 초안`에서 `page`·`size` 페이징 파라미터가 명시적으로 추가됐다(2026-09-11).
> ⚠️ `docs/conventions/domains.md`는 song 2개로 집계한다. 팀 셋리스트 추천을 song에 두면 3개가 되므로 **소유 도메인(song vs setlist) 확인 필요.** `API 초안`은 이 3개를 모두 "노래 / 셋리스트"로 묶어만 두어 이 논쟁을 해소하지 못한다.

### setlist

`team_songs` 테이블 소유. 아래 2개가 전부다 — performance는 `team_songs`를 직접 건드리지 않는다.

| 메서드 | 경로 | 기능 | Request | Response | 비고 |
|---|---|---|---|---|---|
| PUT | `/v1/teams/{id}/setlist` | 팀&nbsp;셋리스트&nbsp;저장 | `TeamSetlistUpdateRequest` | `TeamSetlistResponse` * | 7.2 추천 결과에서 선택 |
| PUT | `/v1/performances/{id}/setlist` | 공연&nbsp;셋리스트&nbsp;확정 | `PerformanceSetlistUpdateRequest` | `PerformanceSetlistResponse` * | 진행도(progress)·순서 관리 |

---

## §10. 알림 (notification) · 북마크 (bookmark) · 대시보드 (dashboard) · 파일 (file) — 3팀

### notification

발생은 2·3·4팀 도메인에서 **이벤트로만** 전달받는다 (`NotificationService` 직접 호출 금지).

| 메서드 | 경로 | 기능 | Request | Response |
|---|---|---|---|---|
| GET | `/v1/notifications?unreadOnly=` | 알림&nbsp;목록 | | `NotificationsResponse` |
| PATCH | `/v1/notifications/read` | 알림&nbsp;읽음 | `NotificationReadRequest` | `NotificationReadResponse` * |

### bookmark

| 메서드 | 경로 | 기능 | Request | Response |
|---|---|---|---|---|
| POST | `/v1/bookmarks` | 저장&nbsp;추가 | `BookmarkCreateRequest` | |
| GET | `/v1/bookmarks?targetType=` | 저장&nbsp;목록 | | `PageResponse<BookmarkResponse>` |
| DELETE | `/v1/bookmarks/{id}` | 저장&nbsp;삭제 | | |

### file (`global/file` — 도메인 아님)

1팀(프로필)·4팀(커버·포스터)이 쓰므로 **최우선 착수 대상.**

| 메서드 | 경로 | 기능 | Request | Response |
|---|---|---|---|---|
| POST | `/v1/files/presigned-url` | 업로드&nbsp;URL&nbsp;발급 | `PresignedUrlCreateRequest` | `PresignedUrlResponse` |
| DELETE | `/v1/files/{fileId}` | 파일&nbsp;삭제 † | | |

> † `API 초안`에서 새로 확인된 엔드포인트다(2026-09-11) — 기존 문서에는 없었다. Request/Response DTO가 아직 정의되어 있지 않으니 착수 전 팀 확인 필요.
> 초안에는 `PUT {uploadUrl}`(파일 업로드) 행도 있지만, 이건 presigned URL로 발급받은 **S3 주소에 직접 올리는 클라이언트 동작**이지 moyeorock 백엔드 엔드포인트가 아니다 — 이 표에는 넣지 않는다.

### dashboard

2·3·4팀 Service를 모두 호출한다. **3팀 작업 중 마지막.**

| 메서드 | 경로 | 기능 | Request | Response | 비고 |
|---|---|---|---|---|---|
| GET | `/v1/dashboard` | 대시보드&nbsp;통합 | | `DashboardResponse` | 7.3 / 7.1.1 |

---

## §11. 로그인 (auth) — 1팀

인증이 필요 없는 유일한 그룹. 응답 코드는 원본 기재 그대로.

| 메서드 | 경로 | 기능 | Request | Response | 응답 코드 | 비고 |
|---|---|---|---|---|---|---|
| POST | `/v1/auth/kakao` | 카카오&nbsp;회원가입/로그인 | `UserOAuth2CodeRequest` | `AuthTokenResponse` † | 200, 400 | 이메일 회원과 중복 X |
| POST | `/v1/auth/signup` | 이메일&nbsp;회원가입 | `UserSignupRequest` | `AuthTokenResponse` † | 201, 400, 409 | 카카오 회원과 중복 X |
| POST | `/v1/auth/login` | 이메일&nbsp;로그인 | `UserLoginRequest` | `AuthTokenResponse` † | 200, 400, 401 | |

> † 원본 표에는 Response가 없다. `docs/conventions/dto-naming.md` §1 정의 — `AuthTokenResponse(accessToken, userId, nickname, onboardingCompleted)`를 3곳 공유, 리프레시 토큰 없음.

---

## 알려진 공백 · 검토 필요

| # | 항목 | 내용 |
|---|---|---|
| 1 | DTO 필드 정의 | `docs/specs/dto-spec.md`에 정리 완료. `API 초안`에는 DTO 정보가 없어 기존 정의를 그대로 유지했다. 일부 DTO 페이지는 미작성 |
| 2 | 합주(rehearsal) 제외 | `API 초안`의 합주 행이 `v1/fee/...`(`studentFeeId`) 등 무관한 경로에 엔드포인트 누락까지 있어 이번 반영에서 제외했다(§3). 팀 정리 후 별도 반영 필요 |
| 3 | 총 개수 | 파일 도메인에 `DELETE /v1/files/{fileId}`가 추가되며 전체 개수가 바뀌었다. `docs/conventions/domains.md`·`docs/conventions/dto-naming.md`의 총계 갱신 필요 |
| 4 | performance 6 vs 7 | ✅ 해소(§8) — 원본 행 수는 7(셋리스트 확정 포함), `performance` 도메인 소유 개수는 6 |
| 5 | song 3 vs 2 | 여전히 미해소(§9) — `API 초안`도 song·setlist 소유를 구분하지 않는다 |
| 6 | 위치 불명 메모 | 원본의 "모임장" 단독 메모 2건은 인접 행(공연 생성·곡 검색)에 붙어 있었으나 대상 불확실 |
| 7 | 내보내기 PATCH | ✅ 해소 — 팀·모임 모두 `/status` 접미사로 확정, 노션도 2026-09-11 수정 반영(§2, §7) |
| 8 | 모임원 목록 응답 | `GroupMembersResponse` → `PageResponse<>` 개명 이력은 `API 초안`에 DTO 정보가 없어 갱신되지 않았다. 완성형은 `PageResponse<GroupMemberResponse>` (§7 §) |
| 9 | 경로 단수형 예외 | `join-request` · `recruit-post` 2개는 `API 초안`을 신뢰해 단수형 그대로 반영(2026-09-11 팀 확인). `api-conventions.md`의 "리소스는 복수형" 규칙의 명시적 예외로 기록 |
| 10 | 파일 삭제 신규 | `DELETE /v1/files/{fileId}`가 `API 초안`에서 새로 확인됨(§10). DTO·구현 담당 확인 필요 |

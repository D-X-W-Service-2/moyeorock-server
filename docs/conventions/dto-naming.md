# moyeorock DTO 명명 규약 — v0

대상: 전체 72개 엔드포인트 · 14 도메인
기준: API 명세 v0 · ERD v3 (MySQL 8.0)

---

## 0. 규칙

### 명명 패턴

| 종류 | 패턴 | 예 |
|---|---|---|
| 요청 | `{Entity}{Action}Request` | `TeamCreateRequest` `UserSignupRequest` |
| 응답 (상세) | `{Entity}DetailResponse` | `TeamDetailResponse` |
| 응답 (요약·목록 항목) | `{Entity}SummaryResponse` | `TeamSummaryResponse` |
| 응답 (그 외) | `{Entity}{Shape}Response` | `NicknameCheckResponse` |

### 지켜야 할 6가지

1. **`record`로 선언한다.** 응답 DTO는 불변이어야 하고, 요청 DTO도 setter를 두지 않는다. Jackson 역직렬화는 `record`에서 정상 동작한다.
2. **생성과 수정 요청은 항상 분리한다.** 필드가 지금 같아도 검증 규칙이 갈리는 순간 하나로 묶인 DTO가 발목을 잡는다. (`RehearsalCreateRequest` / `RehearsalUpdateRequest`)
3. **응답은 최대한 재사용한다.** 생성·수정 응답은 상세 응답을 그대로 쓴다. `TeamCreateResponse` 같은 클래스는 만들지 않는다.
4. **페이징 목록에는 전용 DTO를 만들지 않는다.** `PageResponse<TeamSummaryResponse>` 로 끝낸다. `TeamListResponse` 금지.
5. **페이징 없는 고정 목록만 래퍼를 둔다.** 팀원·합주처럼 개수가 작아 페이징하지 않는 응답은 `TeamMembersResponse` 처럼 복수형 래퍼를 쓴다. 최상위 JSON이 배열이면 나중에 필드를 못 늘린다.
6. **한 곳에서만 쓰는 항목은 중첩 `record`로 둔다.** 두 곳 이상에서 쓰이면 최상위로 승격한다.

```java
public record TeamCreateRequest(
        @NotBlank @Size(max = 50) String name,
        @Size(max = 1000) String description,
        Region region,
        @Size(max = 5) List<Genre> genres,
        @NotNull Instrument instrument
) {}

public record UserActivityResponse(
        List<TeamActivity> teams,
        List<GroupActivity> groups,
        List<PerformanceActivity> performances
) {
    public record TeamActivity(TeamSummaryResponse team, TeamRole role, Instrument instrument) {}
    public record GroupActivity(Long id, String name, GroupType type, GroupRole role, String coverImage) {}
    public record PerformanceActivity(Long id, String title, String groupName,
                                      LocalDateTime performedAt, PerformanceStatus status, String teamName) {}
}
```

### 패키지
```
domain/{domain}/dto/request/ ← 요청
domain/{domain}/dto/response/ ← 응답
global/common/dto/ ← 봉투·페이지·공통 응답
```

응답 DTO는 도메인 간 import를 허용한다. `TeamDetailResponse`가 `UserSummaryResponse`를 참조하는 건 정상이다. **엔티티는 절대 도메인을 넘기지 않는다.**

### 전역 공통 DTO — `global/common/dto`

| 클래스 | 용도 |
|---|---|
| `ApiResponse<T>` | 모든 응답 봉투 `{success, data, error}` |
| `ErrorResponse` | `{code, message, fieldErrors}` |
| `FieldErrorResponse` | `{field, reason}` |
| `PageResponse<T>` | `{content, page, size, totalElements, totalPages, last}` |
| `DeleteResponse` | `{id, deleted}` — hard delete 3곳 공용 |

---

## 1. auth (3)

패키지 `domain/auth`

| 기능 | 메서드 | 경로 | Request | Response |
|---|---|---|---|---|
| 카카오 회원가입·로그인 | POST | `/v0/auth/kakao` | `UserOAuth2CodeRequest` | `AuthTokenResponse` |
| 이메일 회원가입 | POST | `/v0/auth/signup` | `UserSignupRequest` | `AuthTokenResponse` |
| 이메일 로그인 | POST | `/v0/auth/login` | `UserLoginRequest` | `AuthTokenResponse` |

요청 3개는 이미 정해둔 이름을 그대로 썼다.

`AuthTokenResponse(String accessToken, Long userId, String nickname, boolean onboardingCompleted)` 하나를 세 곳에서 공유한다. 회원가입 직후 바로 로그인 상태로 넘기는 전제이고, 가입만 하고 로그인 화면으로 되돌릴 거면 `UserSignupResponse`를 따로 둬야 한다.

`onboardingCompleted`를 여기 넣은 이유는 로그인 직후 온보딩 화면으로 보낼지 대시보드로 보낼지를 프론트가 추가 호출 없이 판단하게 하려는 것이다.

리프레시 토큰을 쓰지 않기로 했으므로 응답에 `refreshToken`은 없다.

---

## 2. user (9)

패키지 `domain/user`

| 기능 | 메서드 | 경로 | Request | Response |
|---|---|---|---|---|
| 내 프로필 조회 | GET | `/v0/users/me` | — | `UserMeResponse` |
| 프로필·설정 수정 | PUT | `/v0/users/me` | `UserUpdateRequest` | `UserMeResponse` |
| 탈퇴 | DELETE | `/v0/users/me` | — | `UserWithdrawResponse` |
| 온보딩 등록 | POST | `/v0/users/me/onboarding` | `OnboardingCreateRequest` | `UserMeResponse` |
| 세션·실력 수정 | PUT | `/v0/users/me/instruments` | `UserInstrumentUpdateRequest` | `UserInstrumentsResponse` |
| 내 활동 | GET | `/v0/users/me/activities` | — | `UserActivityResponse` |
| 닉네임 중복 확인 | GET | `/v0/users/nickname/check` | — | `NicknameCheckResponse` |
| 사용자 검색 | GET | `/v0/users/search` | — | `PageResponse<UserSummaryResponse>` |
| 타인 프로필 조회 | GET | `/v0/users/{id}` | — | `UserProfileResponse` |

**보조 DTO**

| 클래스 | 설명 |
|---|---|
| `UserInstrumentRequest` | `(Instrument instrument, String customInstrument, Level level)` — 온보딩·세션수정 요청 양쪽에서 재사용 |
| `UserInstrumentResponse` | 위에 `id` 추가된 응답형 |
| `UserInstrumentsResponse` | `(List<UserInstrumentResponse> instruments)` |
| `UserSummaryResponse` | **최다 재사용.** team·join·invitation·recruit 전부에서 참조 |
| `UserWithdrawResponse` | `(LocalDateTime withdrawnAt)` |
| `NicknameCheckResponse` | `(String nickname, boolean available)` |

`UserMeResponse`와 `UserProfileResponse`를 나눈 건 노출 범위가 다르기 때문이다. `UserMeResponse`는 `email`·`isRecommendable`·`isActivityPublic`을 담고, `UserProfileResponse`는 절대 담지 않는다. 하나로 합치고 `null` 처리하면 실수로 새어나간다.

`UserUpdateRequest`가 프로필과 설정을 같이 받는다. 설정 전용 DTO를 두지 않는 이유는 화면이 하나라서다.

---

## 3. team (9)

패키지 `domain/team`

| 기능 | 메서드 | 경로 | Request | Response |
|---|---|---|---|---|
| 팀 생성 | POST | `/v0/teams` | `TeamCreateRequest` | `TeamDetailResponse` |
| 팀 목록 | GET | `/v0/teams` | — | `PageResponse<TeamSummaryResponse>` |
| 팀 상세 | GET | `/v0/teams/{id}` | — | `TeamDetailResponse` |
| 팀 정보 수정 | PUT | `/v0/teams/{id}` | `TeamUpdateRequest` | `TeamDetailResponse` |
| 팀 해체 | PATCH | `/v0/teams/{id}/status` | `TeamStatusUpdateRequest` | `TeamStatusResponse` |
| 팀원 목록 | GET | `/v0/teams/{id}/members` | — | `TeamMembersResponse` |
| 팀원 역할·세션 변경 | PATCH | `/v0/teams/{teamId}/members/{userId}` | `TeamMemberUpdateRequest` | `TeamMemberResponse` |
| 팀원 상태 변경(탈퇴·강퇴) | PATCH | `/v0/teams/{teamId}/members/{userId}/status` | `TeamMemberStatusUpdateRequest` | `TeamMemberStatusResponse` |
| 팀원 추천 (AI) | GET | `/v0/teams/{id}/members/recommendations` | — | `TeamMemberRecommendationResponse` |

**보조 DTO**

| 클래스 | 설명 |
|---|---|
| `TeamSummaryResponse` | 목록 항목. activity·join·invitation·recruit에서도 재사용 |
| `TeamMemberResponse` | `(UserSummaryResponse user, TeamRole role, Instrument instrument, MemberStatus status, LocalDateTime joinedAt)` |
| `TeamMembersResponse` | `(List<TeamMemberResponse> members)` |
| `TeamMemberStatusUpdateRequest` | `(MemberStatus status)` — `LEFT`(본인 탈퇴) 또는 `REMOVED`(강퇴)만 허용 |
| `TeamMemberStatusResponse` | `(Long teamId, Long userId, MemberStatus status)` — 탈퇴는 `LEFT`, 강퇴는 `REMOVED` |
| `TeamStatusResponse` | `(Long id, TeamStatus status)` |
| `TeamMemberRecommendationResponse` | `(Instrument instrument, List<Candidate> candidates)`, 중첩 `Candidate(UserSummaryResponse user, double score, List<String> reasons)` |

`TeamDetailResponse`는 참조가 가장 많다. `UserSummaryResponse`(leader) · `TeamMemberResponse` · `TeamSongResponse`(setlist) · `PerformanceSummaryResponse`를 모두 물고 있어서, 여기서 조회 쿼리를 잘못 짜면 N+1이 한 번에 터진다. `@EntityGraph` 또는 `join fetch` 대상.

`TeamMemberUpdateRequest(TeamRole role, Instrument instrument)` — 두 필드 모두 `null` 허용이고, 보낸 것만 반영한다. `role = LEADER`면 팀장 위임이다.

---

## 4. rehearsal (6)

패키지 `domain/rehearsal`

| 기능 | 메서드 | 경로 | Request | Response |
|---|---|---|---|---|
| 합주 생성 | POST | `/v0/teams/{teamId}/rehearsals` | `RehearsalCreateRequest` | `RehearsalDetailResponse` |
| 팀 합주 목록 | GET | `/v0/teams/{teamId}/rehearsals` | — | `RehearsalsResponse` |
| 합주 상세 | GET | `/v0/rehearsals/{id}` | — | `RehearsalDetailResponse` |
| 합주 수정 | PUT | `/v0/rehearsals/{id}` | `RehearsalUpdateRequest` | `RehearsalDetailResponse` |
| 합주 삭제 | DELETE | `/v0/rehearsals/{id}` | — | `DeleteResponse` |
| 내 합주 일정 | GET | `/v0/users/me/rehearsals` | — | `RehearsalsResponse` |

**보조 DTO**

| 클래스 | 설명 |
|---|---|
| `RehearsalSummaryResponse` | 캘린더 항목. `teamId`·`teamName` 포함 — 내 합주 목록에서 팀 구분에 필요 |
| `RehearsalsResponse` | `(List<RehearsalSummaryResponse> rehearsals)` |
| `RehearsalDetailResponse` | `memo` · `createdBy` · `canEdit` 추가 |

`RehearsalCreateRequest`와 `RehearsalUpdateRequest`는 지금 필드가 완전히 같지만 규칙 2에 따라 분리한다. 참가 여부 응답 같은 필드가 붙으면 곧 갈라진다.

목록 응답을 `PageResponse`로 감싸지 않은 이유는 `from`·`to`로 이미 기간이 제한되기 때문이다.

---

## 5. recruit-post (5)

패키지 `domain/recruit`

| 기능 | 메서드 | 경로 | Request | Response |
|---|---|---|---|---|
| 공고 작성 | POST | `/v0/recruit-posts` | `RecruitPostCreateRequest` | `RecruitPostDetailResponse` |
| 공고 목록 | GET | `/v0/recruit-posts` | — | `PageResponse<RecruitPostSummaryResponse>` |
| 공고 상세 | GET | `/v0/recruit-posts/{id}` | — | `RecruitPostDetailResponse` |
| 공고 수정 | PUT | `/v0/recruit-posts/{id}` | `RecruitPostUpdateRequest` | `RecruitPostDetailResponse` |
| 공고 마감·삭제 | PATCH | `/v0/recruit-posts/{id}/status` | `RecruitPostStatusUpdateRequest` | `RecruitPostStatusResponse` |

**보조 DTO**

| 클래스 | 설명 |
|---|---|
| `WantedSlotRequest` | `(Instrument instrument, int count)` — `wanted_slots` JSON 컬럼에 매핑 |
| `WantedSlotResponse` | 위에 `appliedCount` 추가 (`PENDING` 신청 수, 계산값) |
| `RecruitPostSummaryResponse` | `target`을 `TeamSummaryResponse`가 아닌 자체 축약형으로 담는다 — 목록에서 팀 전체를 끌어오면 무겁다 |
| `RecruitPostStatusResponse` | `(Long id, RecruitStatus status)` |

`RecruitPostUpdateRequest`에는 `targetType`·`targetId`가 없다. 대상 변경은 허용하지 않는다.

`RecruitPostDetailResponse.myJoinRequest`는 `MyJoinRequest(Long id, JoinStatus status)` 중첩 record로 두고, 신청 이력이 없으면 `null`이다. join 도메인 DTO를 끌어오지 않는다 — 필요한 필드가 2개뿐이라 의존을 만들 이유가 없다.

---

## 6. join-request (6)

패키지 `domain/join` — 컨트롤러는 `JoinRequestController`

| 기능 | 메서드 | 경로 | Request | Response |
|---|---|---|---|---|
| 가입 신청 | POST | `/v0/join-requests` | `JoinRequestCreateRequest` | `JoinRequestResponse` |
| 받은 신청 목록 | GET | `/v0/join-requests` | — | `PageResponse<JoinRequestResponse>` |
| 내 신청 목록 | GET | `/v0/users/me/join-requests` | — | `PageResponse<JoinRequestResponse>` |
| 신청 승인 | PATCH | `/v0/join-requests/{id}/approve` | — | `JoinApprovedResponse` |
| 신청 거절 | PATCH | `/v0/join-requests/{id}/reject` | — | `JoinDecisionResponse` |
| 신청 취소 | PATCH | `/v0/join-requests/{id}/cancel` | — | `JoinDecisionResponse` |

---

## 7. invitation (6)

**같은 패키지 `domain/join`** — 컨트롤러만 `InvitationController`

| 기능 | 메서드 | 경로 | Request | Response |
|---|---|---|---|---|
| 초대 보내기 | POST | `/v0/invitations` | `InvitationCreateRequest` | `InvitationResponse` |
| 보낸 초대 목록 | GET | `/v0/invitations` | — | `PageResponse<InvitationResponse>` |
| 받은 초대 목록 | GET | `/v0/users/me/invitations` | — | `PageResponse<InvitationResponse>` |
| 초대 수락 | PATCH | `/v0/invitations/{id}/accept` | — | `JoinApprovedResponse` |
| 초대 거절 | PATCH | `/v0/invitations/{id}/decline` | — | `JoinDecisionResponse` |
| 초대 취소 | PATCH | `/v0/invitations/{id}/cancel` | — | `JoinDecisionResponse` |

**§6·§7 공용 DTO** — 엔티티가 `JoinRequest` 하나이므로 결과 응답도 공유한다.

| 클래스 | 설명 |
|---|---|
| `JoinApprovedResponse` | `(Long id, JoinStatus status, LocalDateTime decidedAt, Joined joined)` — 승인·수락 2곳 |
| `JoinDecisionResponse` | `(Long id, JoinStatus status, LocalDateTime decidedAt)` — 거절·취소 4곳 |
| `Joined` | `(TargetType targetType, Long targetId, String role, Instrument instrument)` — 최상위 record |
| `JoinTargetResponse` | `(Long id, String name, Region region, List<Genre> genres)` — 팀·모임 공통 축약형 |

**항목 DTO는 나눈다.** 테이블은 하나지만 노출 필드가 다르다.

| 클래스 | 담는 것 | 안 담는 것 |
|---|---|---|
| `JoinRequestResponse` | `applicant`(= `actor_id`), `recruitPostId` | `targetUser`, `direction` |
| `InvitationResponse` | `inviter`(= `actor_id`), `invitee`(= `target_user_id`) | `recruitPostId`, `direction` |

`direction`은 어느 응답에도 넣지 않는다. 경로로 이미 확정되기 때문이고, 응답에 남기면 프론트가 그걸 보고 분기하는 코드를 짜게 된다.

`InvitationCreateRequest`의 필드명은 `inviteeId`다. `targetUserId`라고 쓰면 `targetId`(팀 id)와 한 글자 차이라 실수한다.

---

## 8. group (11)

패키지 `domain/group`, 공지는 `domain/notice`

| 기능 | 메서드 | 경로 | Request | Response |
|---|---|---|---|---|
| 모임 생성 | POST | `/v0/groups` | `GroupCreateRequest` | `GroupDetailResponse` |
| 모임 상세 (동아리 홈) | GET | `/v0/groups/{id}` | — | `GroupDetailResponse` |
| 모임 정보 수정 | PUT | `/v0/groups/{id}` | `GroupUpdateRequest` | `GroupDetailResponse` |
| 모임원 목록 | GET | `/v0/groups/{id}/members` | — | `PageResponse<GroupMemberResponse>` |
| 모임원 역할 변경 | PATCH | `/v0/groups/{groupId}/members/{userId}` | `GroupMemberUpdateRequest` | `GroupMemberResponse` |
| 모임원 상태 변경(탈퇴·강퇴) | PATCH | `/v0/groups/{groupId}/members/{userId}/status` | `GroupMemberStatusUpdateRequest` | `GroupMemberStatusResponse` |
| 공지 목록 | GET | `/v0/groups/{id}/notices` | — | `PageResponse<NoticeSummaryResponse>` |
| 공지 상세 | GET | `/v0/notices/{id}` | — | `NoticeDetailResponse` |
| 공지 작성 | POST | `/v0/groups/{id}/notices` | `NoticeCreateRequest` | `NoticeDetailResponse` |
| 공지 수정 | PUT | `/v0/notices/{id}` | `NoticeUpdateRequest` | `NoticeDetailResponse` |
| 공지 삭제 | DELETE | `/v0/notices/{id}` | — | `DeleteResponse` |

**보조 DTO**

| 클래스 | 설명 |
|---|---|
| `GroupSummaryResponse` | `UserActivityResponse`에서 재사용 |
| `GroupMemberResponse` | team과 동일 구조(`status`는 `GroupMemberStatus`) — 모임은 인원 상한이 없어 `PageResponse`로 감싼다 |
| `GroupMemberStatusUpdateRequest` | `(GroupMemberStatus status)` — `LEFT`(본인 탈퇴) 또는 `BANNED`(강퇴)만 허용 |
| `GroupMemberStatusResponse` | `(Long groupId, Long userId, GroupMemberStatus status)` — 탈퇴는 `LEFT`, 강퇴는 `BANNED` |
| `NoticeSummaryResponse` | 목록 항목. `body` 제외 |
| `NoticeDetailResponse` | 상세 + 생성·수정 응답(지켜야 할 6가지 #3). `body` 포함 |

목록·상세 분리는 팀 결정으로 추가한 것이다 — 원래 명세는 목록 조회 하나가 `body`까지 포함해 상세 역할을 겸했다(`docs/specs/api-spec.md` §7 ‖).

모임원 역할 변경(모임장 위임)은 원래 표에 없어서 추가한 항목이다. 없으면 모임장이 영구히 탈퇴할 수 없다. `GroupMemberUpdateRequest(GroupRole role)`.

`GroupDetailResponse`는 동아리 홈 화면이므로 `notices`(고정 공지 몇 건)와 `upcomingPerformances`를 함께 담는다. 공연 포스터를 눌러 공연 정보로 넘어가는 7.4 흐름을 한 번의 호출로 지원하려는 것이다.

---

## 9. performance (6 + 셋리스트 1)

패키지 `domain/performance`

| 기능 | 메서드 | 경로 | Request | Response |
|---|---|---|---|---|
| 공연 생성 | POST | `/v0/groups/{groupId}/performances` | `PerformanceCreateRequest` | `PerformanceDetailResponse` |
| 모임 공연 목록 | GET | `/v0/groups/{groupId}/performances` | — | `PageResponse<PerformanceSummaryResponse>` |
| 공연 상세 | GET | `/v0/performances/{id}` | — | `PerformanceDetailResponse` |
| 공연 정보 수정 | PUT | `/v0/performances/{id}` | `PerformanceUpdateRequest` | `PerformanceDetailResponse` |
| 공연 상태 변경 | PATCH | `/v0/performances/{id}/status` | `PerformanceStatusUpdateRequest` | `PerformanceStatusResponse` |
| 공연 내 팀 생성 | POST | `/v0/performances/{performanceId}/teams` | `PerformanceTeamCreateRequest` | `TeamDetailResponse` |
| 공연 셋리스트 확정 | PUT | `/v0/performances/{id}/setlist` | `SetlistConfirmRequest` | `SetlistResponse` |

이 표의 마지막 행(공연 셋리스트 확정)은 `setlist` 도메인 소유다 — 아래 보조 DTO 설명 참고.

**보조 DTO**

| 클래스 | 설명 |
|---|---|
| `PerformanceSummaryResponse` | `TeamDetailResponse`·`GroupDetailResponse`·`UserActivityResponse`에서 재사용 |
| `PerformanceStatusResponse` | `(Long id, PerformanceStatus status)` |
| — | 셋리스트 DTO는 `domain/setlist`에 둔다. §10 참고 |

`PerformanceTeamCreateRequest`는 `TeamCreateRequest`와 필드가 겹치지만 상속·재사용하지 않는다. 경로에서 `performanceId`가 이미 정해지므로 검증 규칙이 다르고, `domain/performance`가 `domain/team`의 요청 DTO를 import하면 의존 방향이 뒤집힌다. 응답은 `TeamDetailResponse`를 재사용한다 — 응답 DTO의 도메인 간 참조는 허용 범위다.

공연 상태 변경은 부수효과가 큰 엔드포인트다. `DONE`으로 바뀌면 소속 팀이 자동 해체된다. 응답에 영향받은 팀 수를 넣을지는 결정 필요.

---

## 10. song · setlist (4 + performance에서 1)

패키지 `domain/song`, 셋리스트는 `domain/setlist`

| 기능 | 메서드 | 경로 | Request | Response |
|---|---|---|---|---|
| 곡 검색 | GET | `/v0/songs` | — | `PageResponse<SongResponse>` |
| 내 취향 추천곡 | GET | `/v0/users/me/songs/recommendations` | — | `SongRecommendationResponse` |
| 팀 셋리스트 추천 (AI) | GET | `/v0/teams/{id}/songs/recommendations` | — | `TeamSongRecommendationResponse` |
| 팀 셋리스트 저장 | PUT | `/v0/teams/{id}/setlist` | `TeamSetlistUpdateRequest` | `TeamSetlistResponse` |
| 공연 셋리스트 확정 | PUT | `/v0/performances/{id}/setlist` | `SetlistConfirmRequest` | `SetlistResponse` |

**보조 DTO**

| 클래스 | 설명 |
|---|---|
| `SongResponse` | `(Long id, String title, String artist, Genre genre, String songKey, Integer bpm, Map<Instrument, Level> difficulty)` |
| `SongSummaryResponse` | `(Long id, String title, String artist)` — 셋리스트 항목용 축약형 |
| `TeamSongResponse` | `(Long id, SongSummaryResponse song, int sortOrder, SongProgress progress)` — team·performance 양쪽에서 재사용 |
| `TeamSetlistUpdateRequest` | `(List<Item> items)`, 중첩 `Item(Long songId, int sortOrder, SongProgress progress)` |
| `SetlistConfirmRequest` | `(List<Item> items)`, 중첩 `Item(Long teamId, Long songId, int sortOrder)` — 공연 확정용 |
| `SetlistResponse` | `(Long performanceId, List<TeamSongResponse> setlist)` |
| `SongRecommendationResponse` · `TeamSongRecommendationResponse` | 추천 근거(`reasons`) 포함 여부가 달라 분리 |

`difficulty`를 `Map<Instrument, Level>`로 받는 건 `songs.difficulty` JSON 컬럼 구조를 그대로 반영한 것이다.

추천 2개는 **구현 방식 미정** 항목이다. 외부 모델을 붙이면 동기 응답이 불가능해져 `SongRecommendationJobResponse`(작업 id) + 폴링 조회가 추가된다. 지금 이름은 룰 기반 전제다.

---

## 11. notification · bookmark · file · dashboard (6)

| 기능 | 메서드 | 경로 | Request | Response |
|---|---|---|---|---|
| 알림 목록 | GET | `/v0/notifications` | — | `NotificationsResponse` |
| 알림 읽음 | PATCH | `/v0/notifications/read` | `NotificationReadRequest` | `NotificationReadResponse` |
| 저장 목록 | GET | `/v0/bookmarks` | — | `PageResponse<BookmarkResponse>` |
| 저장 추가 | POST | `/v0/bookmarks` | `BookmarkCreateRequest` | `BookmarkResponse` |
| 저장 삭제 | DELETE | `/v0/bookmarks/{id}` | — | `DeleteResponse` |
| 업로드 URL 발급 | POST | `/v0/files/presigned-url` | `PresignedUrlCreateRequest` | `PresignedUrlResponse` |
| 대시보드 통합 | GET | `/v0/dashboard` | — | `DashboardResponse` |

**보조 DTO**

| 클래스 | 설명 |
|---|---|
| `NotificationsResponse` | `(int unreadCount, PageResponse<NotificationResponse> notifications)` — 배지 숫자 때문에 래퍼가 필요하지만, 알림은 상한이 없으므로 목록 자체는 페이징한다 |
| `NotificationResponse` | `(Long id, NotificationType type, String message, NotificationTargetType targetType, Long targetId, boolean isRead, LocalDateTime createdAt)` |
| `NotificationReadRequest` | `(List<Long> ids)` — 빈 배열이면 전체 읽음 |
| `NotificationReadResponse` | `(int readCount, int unreadCount)` |
| `BookmarkCreateRequest` | `(BookmarkTargetType targetType, Long targetId)` |
| `BookmarkResponse` | `(Long id, BookmarkTargetType targetType, Long targetId, BookmarkTarget target, LocalDateTime createdAt)` |
| `BookmarkTarget` | `(Long id, String name, Region region, List<Genre> genres, String imageUrl)` — 중첩 record |
| `PresignedUrlCreateRequest` | `(FileDomain domain, String fileName, String contentType)` |
| `PresignedUrlResponse` | `(String uploadUrl, String fileUrl, LocalDateTime expiresAt)` |

`BookmarkTargetType`(`TEAM|USER|SONG`) · `NotificationTargetType`(`TEAM|GROUP|PERFORMANCE|NOTICE|JOIN_REQUEST|INVITATION`)은 `TargetType`(`TEAM|GROUP`)과 값이 다르다. **이름이 비슷하니 각각 별도 enum으로 두고 재사용하지 않는다.** 공유하면 잘못된 값이 검증을 통과한다.

`BookmarkTarget`은 팀·사용자·곡 세 타입을 한 형태로 담는다. 해당 없는 필드는 `null`이다(곡은 `region`이 없고, 사용자는 `genres`가 있다). **대상이 삭제·해체됐으면 `target` 전체가 `null`**이고 프론트가 "삭제된 항목"으로 표시한다 — 조회에서 제외하지 않는 이유는 사용자가 직접 지울 수 있어야 하기 때문이다.

`DashboardResponse(List<RehearsalSummaryResponse> upcomingRehearsals, List<RecruitPostSummaryResponse> openRecruitPosts, List<SongSummaryResponse> recommendedSongs, int unreadNotificationCount)` — 4.1 화면 배치 순서와 필드 순서를 맞췄다. 다른 도메인의 응답 DTO 4종을 조합하므로, dashboard는 각 도메인 **Service만** 호출하고 Repository에 직접 접근하지 않는다.

---

## 12. 정리

| 구분 | 개수 |
|---|---|
| 엔드포인트 | 73 |
| Request DTO | 34 |
| Response DTO | 56 |
| 전역 공통 | 5 |

요청 DTO가 34개뿐인 이유는 73개 중 **42개가 요청 바디를 갖지 않기** 때문이다(GET 전부 + 상태 전이 PATCH 대부분). 조회 조건은 쿼리 파라미터로 받고, `@ModelAttribute` 검색 조건 객체(`TeamSearchCondition` 등)를 둘지는 각 담당이 판단한다 — 파라미터가 3개를 넘으면 만드는 쪽을 권한다.

(공지 상세 조회 `GET /v0/notices/{id}` 추가분 반영 — `docs/specs/api-spec.md` §7 ‖)

### 재사용 상위 5개

| DTO | 참조하는 곳 |
|---|---|
| `UserSummaryResponse` | user · team · group · join · invitation · recruit |
| `TeamSummaryResponse` | team · user(activity) · join · invitation · recruit |
| `PerformanceSummaryResponse` | performance · team · group · user(activity) |
| `TeamSongResponse` | team(상세) · setlist · performance |
| `JoinDecisionResponse` | join 4곳 (거절·취소 ×2) |

이 5개는 **여러 사람이 동시에 건드린다.** 필드를 추가·변경할 때 혼자 고치면 다른 담당의 응답 스펙이 말없이 바뀐다. 변경 시 공유 필수.

### 착수 순서상 먼저 만들어야 하는 것

1. `global/common/dto` 5개 — 나머지 전부가 의존한다
2. `global/common/enums` — DTO 필드 타입이 여기 있다
3. `UserSummaryResponse` — 재사용 1위
4. 이후 도메인별 병렬
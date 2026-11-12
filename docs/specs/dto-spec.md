# DTO 명세 — API 명세서 v0 기준

- **원본**: Notion `API 명세서 v0` DTO 하위 페이지
- 절 구성은 `api-spec.md`와 동일 (§1~§11 — 두 파일에서만 쓰는 좌표다). 네이밍 규칙은 `docs/conventions/dto-naming.md` 참조.
- `PageResponse<T>` 페이지는 **item(T)의 형태만** 정의한다. 페이지 래퍼(content, page, size, totalElements 등)는 `global/common/dto/PageResponse` 공통 정의를 따른다.

## 공용 참조 객체 — 확정 (2026-08-27, 1팀)

원본 JSON 예시가 문자열로만 참조하던 객체들. `docs/conventions/dto-naming.md`의 정식 이름으로 매핑하고 필드를 확정했다.
패키지는 `global/common/dto`가 아니라 **각 소유 도메인의 `dto/response/`다** — 응답 DTO는 도메인 간 import가 허용된다 (`docs/conventions/dto-naming.md` §0 패키지 규칙). `global/common/dto`에는 봉투·공통 응답 3종(`ApiResponse` `PageResponse` `DeleteResponse`)만 둔다.

| 원본 표기 | 정식 이름 | 소유 | 필드 | 상태 |
|---|---|---|---|---|
| `UserSummary` | `UserSummaryResponse` | user (1팀) | `(Long id, String nickname, String profileImage)` | **제안** — 재사용 1위라 최소로. 세션·지역이 필요한 곳(팀원·추천)은 감싸는 DTO가 이미 별도 필드로 가짐 |
| `TeamSummary` | `TeamSummaryResponse` | team (2팀) | `(Long id, String name, Region region, List<Genre> genres, TeamStatus status, int memberCount)` | **확정** (2026-10-31) — 4팀 요청으로 제안 필드 그대로 `status`를 포함한다. `getSummaries` 등은 해체된 팀을 빼서 `ACTIVE`지만, 4팀용 `getSummariesIncludingDisbanded`는 `DISBANDED`도 담는다. 노션 팀 목록·내 팀 목록 응답에도 `status`를 반영해야 한다 |
| `MemberItem` | `TeamMemberResponse` | team (2팀) | `(UserSummaryResponse user, TeamRole role, Instrument instrument, MemberStatus status, LocalDateTime joinedAt)` | 확정 (`dto-naming.md` §3) |
| `RehearsalItem` · `RehearsalSummary` | `RehearsalSummaryResponse` | rehearsal (2팀) | `(Long id, Long teamId, String teamName, String title, LocalDateTime startsAt, LocalDateTime endsAt, String place)` | **구현 완료** (2026-11-02) — 필드는 제안 그대로이고 3팀 dashboard의 `upcomingRehearsals`도 같은 필드를 쓴다. 바꿀 때는 3팀과 공유 |
| `RecruitPostSummary` | `RecruitPostSummaryResponse` | recruit (3팀) | 본문 §4 JSON 예시 그대로 (`target`은 자체 축약형) | 확정 (`dto-naming.md` §5) |

`PageResponse<T>` 래퍼 필드도 `docs/conventions/dto-naming.md` §0에 이미 확정되어 있다: `{content, page, size, totalElements, totalPages, last}` + `PageResponse.from(Page<T>)` 변환. 이 파일 상단의 "래퍼는 공통 정의를 따른다" 문구가 가리키는 정의가 이것이다.

### 왜 지금(착수 전) 확정하는가

DTO 필드는 원칙적으로 소유 팀이 착수할 때 정하지만, 아래 3개는 **팀 경계를 넘는 의존의 시작점**이라 착수 전에 정하지 않으면 서로를 블로킹한다.

1. **`UserSummaryResponse` (1팀 소유)** — 재사용 1위. team·group·join·invitation·recruit가 전부 참조하므로(`docs/conventions/dto-naming.md` §12), 1팀이 확정을 미루면 **2·3·4팀 전원이 대기**하게 된다. dto-naming.md 착수 순서에서 공통 DTO·enums 다음 3순위로 못 박힌 이유.
2. **`TeamSummaryResponse` (2팀 소유)** — 방향이 반대인 케이스. 1팀이 만들 `GET /v1/users/me/activities`(`UserActivityResponse.TeamActivity`)와 `GET /v1/users/{id}`(`UserProfileResponse.activities`)가 이걸 참조하므로(`docs/conventions/dto-naming.md` §0 예시 코드), 미확정이면 **1팀 user API 2개가 완성 불가**. 소유는 2팀이지만 1팀이 기다리는 쪽이라 착수 전 합의가 필요해 위 표에 제안을 올렸다.
3. **`PageResponse<T>` (1팀 소유, global/common)** — 모든 팀의 목록 API가 import하는 클래스. 확정이 늦으면 각 팀이 임시 포장을 만들었다가 갈아엎게 된다. 필드는 이미 dto-naming.md §0에 있으므로 구현만 선행하면 된다.

나머지 요약 객체(`RehearsalSummaryResponse` 등)는 1팀이 의존하지 않으므로 소유 팀 착수 시점에 확정해도 늦지 않다 — 단, 재사용 상위 5개는 변경 시 공유 필수 규칙(`docs/conventions/dto-naming.md` §12)을 따른다.

## 빈 DTO (Notion 페이지는 있으나 내용 미작성)

`JoinRequestResponse` · `PageResponse<JoinRequestResponse>` · `PageResponse<InvitationResponse>` · `PageResponse<TeamSummaryResponse>` · `PageResponse<UserSummaryResponse>`

---

## §1. 사용자 (user)

### UserMeResponse

```json
{
  "id": 12,
  "email": "seojun@example.com",
  "nickname": "서준",
  "region": "SEOUL",
  "genres": ["ROCK", "INDIE"],
  "bio": "베이스 3년차입니다.",
  "profileImage": "https://.../a.jpg",
  "platformRole": "USER",
  "loginType": "EMAIL",
  "instruments": [
    { "id": 3, "instrument": "BASS", "level": "INTERMEDIATE" },
    { "id": 4, "instrument": "DRUM", "level": "BEGINNER" }
  ],
  "isRecommendable": true,
  "isActivityPublic": true,
  "onboardingCompleted": true,
  "createdAt": "2026-07-02T11:20:00"
}
```

### UserUpdateRequest

```json
{
  "nickname": "서준밴드",
  "region": "SEOUL",
  "genres": ["ROCK", "INDIE"],
  "bio": "베이스 3년차입니다.",
  "profileImage": "https://.../b.jpg",
  "isRecommendable": true,
  "isActivityPublic": false
}
```

### UserWithdrawResponse

```json
{ "withdrawnAt": "2026-08-11T14:02:00" }
```

직책 보유자는 탈퇴가 막힌다(`docs/specs/api-spec.md` §1 \*). 봉투는 다른 에러와 같고 `message`만 상황 문장이다(2026-11-02).

```json
{ "success": false, "code": "WITHDRAW_BLOCKED_BY_ROLE", "message": "팀 '무명밴드'의 팀장을 위임하고, 모임 '홍대 밴드부'의 모임장을 위임하거나 모임을 해체한 뒤 탈퇴할 수 있습니다.", "data": null }
```

### OnboardingCreateRequest

```json
{
  "nickname": "서준",
  "region": "SEOUL",
  "genres": ["ROCK", "INDIE"],
  "instruments": [
    { "instrument": "BASS", "level": "INTERMEDIATE" },
    { "instrument": "DRUM", "level": "BEGINNER" }
  ]
}
```

### UserInstrumentUpdateRequest

```json
{
  "instruments": [
    { "instrument": "BASS", "level": "ADVANCED" },
    { "instrument": "KEY", "level": "NOVICE" }
  ]
}
```

### UserInstrumentsResponse

`PUT /v1/users/me/instruments`의 응답이다 (`docs/conventions/dto-naming.md` §2).

```json
{
  "instruments": [
    { "id": 8, "instrument": "BASS", "level": "ADVANCED" },
    { "id": 9, "instrument": "KEY", "level": "NOVICE" }
  ]
}
```

### UserActivityResponse

```json
{
  "teams": [
    { "team": "TeamSummary", "role": "LEADER", "instrument": "BASS" }
  ],
  "groups": [
    { "id": 2, "name": "홍대 밴드부", "type": "REGULAR", "role": "OWNER", "coverImage": "https://..." }
  ],
  "performances": [
    {
      "id": 7,
      "title": "2026 정기공연",
      "groupName": "홍대 밴드부",
      "performedAt": "2026-11-22T18:00:00",
      "status": "PLANNED",
      "teamName": "무명밴드"
    }
  ]
}
```

- `teams[]` 항목은 2팀 `TeamMembershipResponse`, `groups[]` 항목은 4팀 `GroupSummaryResponse`를 그대로 담는다(`docs/conventions/dto-naming.md` §0, 2026-11-02). 필터(ACTIVE)·정렬은 제공하는 팀 Service 책임이다.
- `performances[]`는 4팀 `PerformanceService.findActivitiesByUserId`가 준다 — **진행 중인 공연(`PLANNED`·`RECRUITING`)만** 공연일 가까운 순이고, 한 공연에 내 팀이 여럿이면 팀마다 한 줄이라 같은 공연이 `teamName`만 달리 여러 번 나올 수 있다. 해체된 모임의 공연은 빠진다. 내 활동 화면의 공연 타일이 "참여 중인 공연"이고 지난 공연 섹션이 없어 1팀 요청으로 범위를 줄였다(2026-11-02, PR #84 댓글 — 이전 안은 `CANCELED`만 빼고 끝난 공연을 뒤에 붙이는 것). `groups[]`는 4팀 `GroupMemberService.findMyGroupSummaries`(가입 최신순, 페이징 없음)가 준다(이슈 #95). 1팀 `UserActivityService`가 두 메서드를 연결했다(이슈 #126, 2026-11-03).

### UserProfileResponse

```json
{
  "id": 30,
  "nickname": "민서",
  "region": "GYEONGGI",
  "genres": ["JAZZ"],
  "bio": "드럼 칩니다",
  "profileImage": "https://.../c.jpg",
  "instruments": [{ "instrument": "DRUM", "level": "ADVANCED" }],
  "isActivityPublic": true,
  "activities": {
    "teams": ["TeamSummary"],
    "groups": [],
    "performances": []
  },
  "createdAt": "2026-06-11T09:00:00"
}
```

- `instruments[]`는 `{instrument, level}`뿐이다. `UserInstrumentResponse`의 `id`는 타인에게 노출하지 않는다(이슈 #90 P-4).
- `isActivityPublic`은 유지한다. 비공개면 `activities`가 `null`이라, 프론트가 "비공개"와 "활동 없음"(빈 목록 3개)을 구분하려면 이 값이 필요하다(P-3, 2026-11-02 1팀 결정). `email`·`isRecommendable`은 담지 않는다.
- `activities`는 공개면 `GET /v1/users/me/activities`와 같은 조립이다. 세 목록 모두 내 활동과 같은 Service가 채운다(이슈 #126, 2026-11-03).

## §2. 팀 (team)

### TeamCreateRequest

`instrument`는 생성자 본인의 세션이다.

```json
{
  "name": "무명밴드",
  "description": "합주 위주로 편하게 합니다.",
  "region": "SEOUL",
  "genres": ["ROCK", "INDIE"],
  "instrument": "BASS"
}
```

### TeamDetailResponse

외부(독립) 팀은 `performance`가 null.

> `songs`는 4팀 `SetlistService.findTeamSongs`로 채운다(2026-11-04, 이슈 #130) — 고른 곡이 없으면 빈 배열이고, 공연 종료로 해체된 팀은 선택곡이 그대로 남지만 개별 해체한 팀은 해체 때 지워져 빈 배열이다. `performance`는 4팀 `PerformanceMemberService.getBrief`(#105)로 채운다(2026-11-03) — 공연 팀이면 `(id, title, groupName, performedAt)`, 독립 팀이면 `null`, 모임이 해체됐으면 `groupName`만 `null`. `myRole`은 요청자가 팀원이 아니면 null이고, 해체된 팀도 상세를 읽을 수 있다(`status = DISBANDED`, 2026-11-02).

```json
{
  "id": 5,
  "name": "무명밴드",
  "description": "합주 위주로 편하게 합니다.",
  "region": "SEOUL",
  "genres": ["ROCK", "INDIE"],
  "status": "ACTIVE",
  "performance": {
    "id": 7,
    "title": "2026 정기공연",
    "groupName": "홍대 밴드부",
    "performedAt": "2026-11-22T18:00:00"
  },
  "leader": "UserSummary",
  "memberCount": 4,
  "members": ["MemberItem"],
  "songs": [
    {
      "id": 21,
      "song": { "id": 88, "title": "Bohemian Rhapsody", "artist": "Queen" }
    }
  ],
  "myRole": "LEADER",
  "createdAt": "2026-08-01T10:00:00"
}
```

- `songs`는 **팀 선택곡**이다(`TeamSongResponse`). 2026-10-30에 `setlist`(`sortOrder`·`progress` 포함)에서 바꿨다 — 팀 쪽에서는 "셋리스트"라는 말을 쓰지 않는다(이슈 #85, 노션도 수정). 공연 셋리스트로 넘긴 곡·확정곡은 공연 셋리스트 조회(§8)로 본다

### TeamUpdateRequest

전체 교체(PUT)이며 응답 바디는 없다(`data: null`).

```json
{
  "name": "무명밴드",
  "description": "수정된 소개",
  "region": "SEOUL",
  "genres": ["ROCK", "PUNK"]
}
```

### TeamStatusUpdateRequest / TeamStatusResponse

```json
{ "status": "DISBANDED" }
```

```json
{ "id": 5, "status": "DISBANDED" }
```

### TeamMembersResponse

```json
{ "members": ["MemberItem"] }
```

### TeamMemberUpdateRequest

```json
{ "role": "LEADER", "instrument": "DRUM" }
```

### TeamMemberStatusResponse

구 `TeamMemberRemoveResponse`. 탈퇴는 `LEFT`, 강퇴는 `REMOVED`로 내려간다.

```json
{ "teamId": 5, "userId": 30, "status": "REMOVED" }
```

```json
{ "teamId": 5, "userId": 31, "status": "LEFT" }
```

### TeamMemberRecommendationResponse

```json
{
  "instrument": "DRUM",
  "candidates": [
    {
      "user": "UserSummary",
      "score": 0.87,
      "reasons": ["장르 일치: ROCK", "활동 지역 동일", "실력 ADVANCED"]
    }
  ]
}
```

- 점수 높은 순 **최대 10명**이다. 후보가 없으면 `candidates`는 빈 배열이다. 평가 대상 후보는 조건에 맞는 사용자 중 무작위 최대 200명이라, 사용자가 많으면 호출할 때마다 결과가 조금씩 달라질 수 있다. `user`는 `UserSummaryResponse`(`id`·`nickname`·`profileImage`)이고, `score`·`reasons`는 AI 서버가 매긴 값 그대로다(`reasons`가 없으면 빈 배열). 구현: 이슈 #150(2026-11-08).

## §3. 합주 (rehearsal)

### RehearsalCreateRequest

제목(필수, 100자 이하)·시작·종료 시각(필수, 종료가 시작보다 늦어야 함)·장소(선택, 100자 이하)·메모(선택, 20,000자 이하)다. 메모 상한은 공지 본문과 같이 `TEXT` 컬럼 크기를 고려해 정했다(2026-11-03).

```json
{
  "title": "1차 합주",
  "startsAt": "2026-09-14T19:00:00",
  "endsAt": "2026-09-14T21:00:00",
  "place": "홍대 사운드홀 2번방",
  "memo": "베이스 앰프 없음, 각자 지참"
}
```

### RehearsalDetailResponse

생성·수정·상세가 함께 쓴다. `canEdit`은 요청자가 작성자이거나 팀장일 때 true다. 작성자가 탈퇴했으면 `createdBy`는 null이다.

```json
{
  "id": 41,
  "team": { "id": 5, "name": "무명밴드" },
  "title": "1차 합주",
  "startsAt": "2026-09-14T19:00:00",
  "endsAt": "2026-09-14T21:00:00",
  "place": "홍대 사운드홀 2번방",
  "memo": "베이스 앰프 없음, 각자 지참",
  "createdBy": "UserSummary",
  "canEdit": true,
  "createdAt": "2026-08-11T14:00:00"
}
```

### RehearsalsResponse

```json
{ "rehearsals": ["RehearsalItem"] }
```

### RehearsalUpdateRequest

전체 교체(PUT)다. 노션 DTO 페이지는 비어 있고 API 초안의 "합주 일정 수정" 표(생성 요청과 같은 5개 필드, 추정)를 따라 구현했다(2026-11-02). 제목(필수, 100자 이하)·시작·종료 시각(필수, 종료가 시작보다 늦어야 함)·장소(선택, 100자 이하)·메모(선택, 20,000자 이하)이고, 보내지 않은 장소·메모는 비워진다. 종료·시작 비교는 DTO가 아니라 Service가 검사한다.

```json
{
  "title": "1차 합주 (변경)",
  "startsAt": "2026-09-14T20:00:00",
  "endsAt": "2026-09-14T22:00:00",
  "place": "홍대 사운드홀 3번방",
  "memo": "장소 변경됨"
}
```

## §4. 모집 공고 (recruit)

### RecruitPostCreateRequest

```json
{
  "targetType": "TEAM",
  "targetId": 5,
  "title": "베이스 1명 구합니다",
  "body": "주 1회 홍대에서 합주합니다.",
  "wantedSlots": [
    { "instrument": "BASS", "count": 1 },
    { "instrument": "KEY", "count": 1 }
  ],
  "region": "SEOUL"
}
```

### PageResponse\<RecruitPostSummaryResponse\>

```json
{
  "id": 14,
  "targetType": "TEAM",
  "target": { "id": 5, "name": "무명밴드", "region": "SEOUL", "genres": ["ROCK"] },
  "title": "베이스 1명 구합니다",
  "wantedSlots": [{ "instrument": "BASS", "count": 1 }],
  "region": "SEOUL",
  "status": "OPEN",
  "createdAt": "2026-08-11T15:00:00"
}
```

### RecruitPostDetailResponse

```json
{
  "id": 14,
  "targetType": "TEAM",
  "target": { "id": 5, "name": "무명밴드", "region": "SEOUL", "genres": ["ROCK"] },
  "author": { "id": 12, "nickname": "김철수" },
  "title": "베이스 1명 구합니다",
  "body": "주 1회 홍대에서 합주합니다.",
  "wantedSlots": [
    { "instrument": "BASS", "count": 1, "appliedCount": 3 }
  ],
  "region": "SEOUL",
  "status": "OPEN",
  "canEdit": false,
  "myJoinRequest": { "id": 77, "status": "PENDING" },
  "createdAt": "2026-08-11T15:00:00",
  "updatedAt": "2026-08-11T15:00:00"
}
```

> 2026-09-25 Notion `API 초안` 공고 행과 대조해 반영했다.
> - `updatedAt`이 추가됐다(작성·상세·수정 응답 공통). 이전 예시에는 `createdAt`만 있었다.
> - `target`은 `TeamSummary` 자리표시자가 아니라 **recruit 자체 축약형** `{id, name, region, genres}`이다(`docs/conventions/dto-naming.md` §5). `author`는 `UserSummaryResponse`이며 Notion 예시는 `{id, nickname}`만 있다.
> - `wantedSlots[].appliedCount`는 대기(`PENDING`) 신청 수. 작성 직후 응답 예시는 `0`이다.
> - `canEdit`은 수정 권한 여부이고 Notion Actor가 `모임장 / 팀장`이다. `myJoinRequest`는 신청 이력이 없으면 `null`이며 Notion에 "상의 필요" 메모가 있다.
> - **구현 현황 (2026-11-03)**: `author`는 `UserSummaryResponse`로 채우고 탈퇴한 작성자면 `null`이다. `target`은 팀이면 이름·지역·장르를, 모임이면 `GroupBriefResponse`가 이름만 줘서 **이름만** 채운다(`region`·`genres`는 `null`, 4팀 일괄 조회가 생기면 채움). 해체되거나 없는 팀·모임은 공고를 빼지 않고 `target`에 `id`만 남는다(명세가 `target`을 non-null로 정의해 객체를 유지). `wantedSlots[].appliedCount`는 그 세션의 대기(`PENDING`) 신청 수이고, `myJoinRequest`는 보는 사람이 그 공고에 낸 가장 최근 신청(`{id, status}`)이다 — 비로그인이거나 신청한 적이 없으면 `null`이다(2026-11-03 join 구현으로 채움).

### RecruitPostUpdateRequest

```json
{
  "title": "베이스 1명 구합니다",
  "body": "주 1회 홍대에서 합주합니다.",
  "wantedSlots": [
    { "instrument": "BASS", "count": 1 },
    { "instrument": "KEY", "count": 1 }
  ],
  "region": "SEOUL"
}
```

### RecruitPostStatusUpdateRequest / RecruitPostStatusResponse

```json
{ "status": "CLOSED" }
```

```json
{ "id": 14, "status": "CLOSED" }
```

## §5. 가입 신청 (join)

### JoinRequestCreateRequest

```json
{
  "targetType": "TEAM",
  "targetId": 5,
  "recruitPostId": 14,
  "instrument": "BASS",
  "message": "베이스 3년 쳤습니다."
}
```

### JoinRequestResponse / PageResponse\<JoinRequestResponse\>

```json
{
  "id": 77,
  "targetType": "TEAM",
  "target": { "id": 5, "name": "무명밴드", "region": "SEOUL", "genres": ["ROCK"] },
  "applicant": { "id": 30, "nickname": "홍길동", "profileImage": null },
  "recruitPostId": 14,
  "instrument": "BASS",
  "message": "베이스 3년 쳤습니다.",
  "status": "PENDING",
  "createdAt": "2026-08-11T15:10:00"
}
```

> 2026-11-03 Notion `API 초안`(가입 신청)과 대조해 반영했다. `direction`·`targetUser`는 담지 않는다. `instrument`는 요청·응답 모두 **단수**다(Notion 요청이 `List`("다중 고려")였으나 단수로 확정). `message`는 요청에도 선택 필드로 둔다(초안 요청 표에는 빠져 있었다). 모임 대상은 `target.region`·`target.genres`가 `null`이고 `instrument`도 `null`이다. 해체된 대상은 `target`에 `id`만 남고, 탈퇴한 신청자는 `applicant`가 `null`이다.

### JoinRequestStatusUpdateRequest / InvitationStatusUpdateRequest

```json
{ "status": "APPROVED" }
```

신청은 `APPROVED` · `REJECTED` · `CANCELED`, 초대는 `ACCEPTED` · `DECLINED` · `CANCELED`다. Notion 초안에는 요청 바디가 없어("Request Body 없음") 상태 변경을 `PATCH /{id}/status` 하나로 합치면서 서버가 정했다(2026-11-03).

### JoinApprovedResponse (승인)

```json
{
  "id": 77,
  "status": "APPROVED",
  "decidedAt": "2026-08-11T17:00:00",
  "joined": { "targetType": "TEAM", "targetId": 5, "role": "MEMBER", "instrument": "BASS" }
}
```

### JoinDecisionResponse (거절·취소)

```json
{ "id": 77, "status": "REJECTED", "decidedAt": "2026-08-11T17:00:00" }
```

```json
{ "id": 77, "status": "CANCELED", "decidedAt": "2026-08-11T17:05:00" }
```

## §6. 초대 (join · invitation)

### InvitationCreateRequest

```json
{
  "targetType": "TEAM",
  "targetId": 5,
  "inviteeId": 30,
  "instrument": "DRUM",
  "message": "드럼 자리 비어 있어요, 같이 하실래요?"
}
```

### InvitationResponse / PageResponse\<InvitationResponse\>

```json
{
  "id": 91,
  "targetType": "TEAM",
  "target": { "id": 5, "name": "무명밴드", "region": "SEOUL", "genres": ["ROCK"] },
  "inviter": { "id": 12, "nickname": "김철수", "profileImage": null },
  "invitee": { "id": 30, "nickname": "이영희", "profileImage": null },
  "instrument": "DRUM",
  "message": "드럼 자리 비어 있어요, 같이 하실래요?",
  "status": "PENDING",
  "createdAt": "2026-08-11T16:00:00"
}
```

> 2026-11-03 Notion `받은 초대 목록` 응답 표와 대조해 반영했다(보낸 목록도 같은 모양). `recruitPostId`·`direction`은 담지 않는다. `inviter`·`invitee`는 탈퇴한 사용자면 `null`이다.

### JoinApprovedResponse / JoinDecisionResponse

가입(§5)과 같은 형태다. Notion에는 태그만 다른 중복 페이지로 존재 — **클래스는 하나만 만든다** (`join_requests` 테이블 공유와 같은 원리).

---

## §7. 모임 (group) · 공지 (notice)

### GroupCreateRequest / GroupUpdateRequest

두 DTO의 원본 예시가 동일하다.

```json
{
  "name": "홍대 밴드부",
  "description": "매주 토요일 합주하는 동아리입니다.",
  "type": "REGULAR",
  "region": "SEOUL",
  "coverImage": "https://.../cover.jpg"
}
```

### GroupDetailResponse

```json
{
  "id": 2,
  "name": "홍대 밴드부",
  "description": "매주 토요일 합주하는 동아리입니다.",
  "type": "REGULAR",
  "region": "SEOUL",
  "coverImage": "https://.../cover.jpg",
  "owner": "UserSummary",
  "memberCount": 24,
  "myRole": "MEMBER",
  "pinnedNotices": [
    { "id": 8, "title": "3월 정기공연 안내", "isPinned": true, "createdAt": "2026-08-01T09:00:00" }
  ],
  "upcomingPerformances": [
    {
      "id": 7,
      "title": "2026 정기공연",
      "performedAt": "2026-11-22T18:00:00",
      "venue": "홍대 롤링홀",
      "posterImage": "https://.../poster.jpg",
      "status": "PLANNED",
      "createdAt": "2026-09-11T10:00:00"
    }
  ],
  "createdAt": "2026-07-10T12:00:00"
}
```

- `pinnedNotices`는 **고정된 공지만** 최신순 최대 3건이다. 고정 공지가 없으면 빈 배열이고 일반 공지로 자리를 채우지 않는다(2026-10-07 확정). 전체 공지는 공지 목록 엔드포인트로 받는다
- `upcomingPerformances`는 `PLANNED`·`RECRUITING` 공연을 공연일 가까운 순으로 **최대 3건**이다(모임 공연 목록 `period=UPCOMING`과 같은 기준). 없으면 빈 배열이고 필드는 항상 내린다(2026-11-03, #112 — PR #67 리뷰에서 빈 배열 권고). 항목은 공연 목록과 같은 `PerformanceSummaryResponse`라 노션 예시에 없는 `createdAt`도 들어간다. 전체는 공연 목록 엔드포인트로 받는다

### PageResponse\<GroupSummaryResponse\>

`GET /v1/groups/my?keyword=&page=&size=`(내 모임 목록)의 응답 항목. 2026-10-29 추가(이슈 #80).

```json
{ "id": 2, "name": "홍대 밴드부", "type": "REGULAR", "role": "OWNER", "coverImage": "https://.../cover.jpg" }
```

- `role`은 그 모임에서의 **내 역할**이다
- `UserActivityResponse.groups[]` 항목과 같은 모양이다(`docs/conventions/dto-naming.md` §8 — `GroupSummaryResponse`를 거기서 재사용). 노션 `API 초안`의 이 행은 본문이 비어 있어 이미 명세에 있던 이 모양을 그대로 썼다
- 모임원 수·지역 등은 담지 않는다(명세에 없음)

### PageResponse\<GroupMemberResponse\>

`GET /v1/groups/{id}/members?keyword=&page=&size=`의 응답 항목. **2026-10-05에 Notion `API 초안`(2026-09-23) 기준으로 교체**했다 — 이전 스냅샷은 `user`를 중첩하고 `status`를 담는 형태였다.

```json
{
  "userId": 12,
  "nickname": "서준",
  "profileImage": "https://.../a.jpg",
  "instruments": [
    { "id": 3, "instrument": "BASS", "level": "INTERMEDIATE" }
  ],
  "role": "OWNER",
  "joinedAt": "2026-07-10T12:00:00"
}
```

- `status`는 담지 않는다 — 목록이 `ACTIVE`만 조회하므로 전부 같은 값이다
- `instruments[]`에 `customInstrument`가 없다. Notion 예시에는 남아 있으나 **ETC·`custom_instrument` 폐지(2026-10-01)가 더 최신**이다
- `nickname`·`profileImage`·`instruments`는 user 도메인 소유라 `UserService`를 통해 채운다

### GroupMemberUpdateRequest

```json
{ "role": "OWNER" }
```

`role`은 `OWNER`·`MANAGER`·`MEMBER`. `MANAGER`는 값만 있고 권한은 없다(`erd.md` §5).

### 모임원 상태 변경 (탈퇴·강퇴)

`PATCH /v1/groups/{groupId}/members/{userId}/status` — **요청 바디·응답 바디가 모두 없다**(`data: null`). 2026-10-05에 Notion 기준으로 확정했고, 이전 스냅샷에 있던 `GroupMemberRemoveResponse`는 걷었다.

상태값은 서버가 판단한다 — 요청자 == 대상이면 `LEFT`(본인 탈퇴), 모임장이 타인에게 하면 `BANNED`(강퇴).

### NoticeCreateRequest / NoticeUpdateRequest

두 DTO의 원본 예시가 동일하다.

```json
{
  "title": "3월 정기공연 안내",
  "body": "3월 22일 롤링홀에서 정기공연을 진행합니다.",
  "isPinned": true
}
```

### PageResponse\<NoticeSummaryResponse\>

목록·상세 분리(팀 결정, `docs/specs/api-spec.md` §7 ‖)로 `body`를 뺀 목록 전용 항목이다. 원본 노션은 이 구조에 `body`까지 포함했다.

```json
{
  "id": 8,
  "groupId": 2,
  "title": "3월 정기공연 안내",
  "isPinned": true,
  "createdAt": "2026-08-01T09:00:00",
  "updatedAt": "2026-08-02T11:00:00"
}
```

### NoticeDetailResponse

`GET /v1/notices/{id}`, 그리고 생성·수정 응답(`dto-naming.md` 지켜야 할 6가지 #3에 따라 재사용)에도 쓴다. `body`를 포함한 전체 필드.

```json
{
  "id": 8,
  "groupId": 2,
  "title": "3월 정기공연 안내",
  "body": "3월 22일 롤링홀에서 정기공연을 진행합니다. 참가 신청은...",
  "isPinned": true,
  "createdAt": "2026-08-01T09:00:00",
  "updatedAt": "2026-08-02T11:00:00"
}
```

**작성자(`author`)를 담지 않는다** (2026-10-05 결정). 공지 작성·수정·삭제 권한이 현재 모임장으로 고정되어 있어 화면에서 작성자를 구분할 이유가 없다. `group_notices.author_id` 컬럼은 이력 추적용으로 유지하고 저장 시에만 채운다 — 나중에 노출이 필요해지면 응답에만 다시 더하면 된다.

## §8. 공연 (performance)

### PerformanceCreateRequest / PerformanceUpdateRequest

두 DTO의 원본 예시가 동일하다.

```json
{
  "title": "2026 정기공연",
  "description": "1년에 한 번 하는 정기공연입니다.",
  "performedAt": "2026-11-22T18:00:00",
  "venue": "홍대 롤링홀",
  "posterImage": "https://.../poster.jpg"
}
```

- `title`·`performedAt`·`venue` 필수, `description`·`posterImage` 선택 — `API 초안`(2026-10-08) 기준. `venue`는 이전 `erd.md`에서 "필수 X"였으나 노션 기준으로 필수다(2026-10-30, 이슈 #83)
- 길이: `title`·`venue` 100자, `description` 1,000자(모임 소개와 같은 기준 — 긴 안내는 공지로), `posterImage` 500자
- `posterImage`는 파일 API(`POST /v1/files/presigned-url`, `domain=PERFORMANCE_POSTER`)로 받은 `fileUrl` 문자열을 그대로 보낸다. 공연 쪽은 URL만 저장한다(`erd.md` §17)
- 끝난 공연(`DONE`·`CANCELED`)은 수정할 수 없다 — 409 `INVALID_STATE`(2026-11-03, PR #104 리뷰). 상태 변경·공연 내 팀 생성·셋리스트 확정과 같이 끝난 공연은 기록으로 둔다. 일시가 지났어도 아직 끝나지 않은 공연은 일시를 그대로 두면 고칠 수 있다

### PageResponse\<PerformanceSummaryResponse\>

```json
{
  "id": 7,
  "title": "2026 정기공연",
  "performedAt": "2026-11-22T18:00:00",
  "venue": "홍대 롤링홀",
  "posterImage": "https://.../poster.jpg",
  "status": "PLANNED",
  "createdAt": "2026-09-11T10:00:00"
}
```

- **2026-10-30 `API 초안` 대조(이슈 #83)**: `teamCount`를 빼고 `createdAt`을 넣었다
- `status`는 실제 공연 상태 `PerformanceStatus`(`PLANNED|RECRUITING|DONE|CANCELED`)다. 목록 쿼리 `period`(`UPCOMING`·`PAST`)는 거르는 데만 쓰고 응답 `status`로 내리지 않는다 — 노션 예시의 `"status": "UPCOMING"`은 `PLANNED`로 노션 정정 완료(2026-11-02, `api-spec.md` §8 ※·공백 #13)

### PerformanceDetailResponse

공연 생성·상세·수정 공통 응답.

```json
{
  "id": 7,
  "group": { "id": 2, "name": "홍대 밴드부", "coverImage": "https://.../cover.jpg" },
  "title": "2026 정기공연",
  "description": "1년에 한 번 하는 정기공연입니다.",
  "performedAt": "2026-11-22T18:00:00",
  "venue": "홍대 롤링홀",
  "posterImage": "https://.../poster.jpg",
  "status": "PLANNED",
  "teams": ["TeamSummary"],
  "setlist": [
    {
      "teamId": 5,
      "teamName": "무명밴드",
      "song": { "id": 88, "title": "Bohemian Rhapsody", "artist": "Queen" },
      "sortOrder": 1
    }
  ],
  "createdBy": "UserSummary",
  "canEdit": true,
  "myTeamIds": [5],
  "createdAt": "2026-08-01T10:00:00"
}
```

- `setlist`는 확정된 것만 확정 순서(`sortOrder`)대로 담는다 (원본 메모). 항목은 `SetlistItemResponse`(공연 셋리스트 조회와 같은 모양)다. **확정 전이면 빈 목록**이고 `null`은 보내지 않는다. 노션은 Nullable O라 빈 목록도 그 정의 안이다. **빈 목록이 곧 확정 전은 아니다** — 확정 뒤 팀이 개별 해체되면 그 팀의 셋리스트 행이 지워져(`architecture.md` §6 예외), 확정곡을 넘긴 팀이 모두 해체되면 확정 상태여도 빈 목록이고, 일부만 해체되면 `sortOrder`가 1, 3처럼 빈다. 프론트는 확정 여부를 셋리스트 조회(`GET /v1/performances/{id}/setlist`)의 `confirmed`로 보고, 순서는 `sortOrder` 숫자가 아니라 배열 순서를 쓴다(2026-11-06, PR #146 리뷰). 끝난 공연(`DONE`·`CANCELED`)도 기록이라 그대로 보이고, 팀이 해체됐어도 `teamName`이 나온다(2026-11-05, 이슈 #145)
- `teams`는 해체되지 않은 참여 팀이라 끝난 공연(`DONE`·`CANCELED`)이면 빈 목록이다 — 끝난 공연은 참여 팀을 보여주지 않는다(2026-10-30 결정, 2026-11-01 취소도 팀을 해체하기로 해서 함께)
- `myTeamIds`는 요청자가 속한, 이 공연의 참여 팀 id 목록이다(없으면 빈 목록). 한 사용자가 같은 공연의 여러 팀에 속할 수 있어 **2026-10-30에 `myTeamId`(단일)에서 바꿨다**(노션도 수정, 이슈 #79 댓글)
- `canEdit`은 요청자가 그 모임의 모임장인지다
- `createdBy`는 공연을 만든 사람이 탈퇴했으면 `null`이다(노션은 Nullable X — 탈퇴 회원 표시 정책이 생기면 맞춘다)
- `teams`·`myTeamIds`는 이슈 #88에서(`api-spec.md` §8 ⊕), `setlist`는 이슈 #145에서 추가했다. 생성·수정 응답도 같은 DTO라 함께 채워지고, 막 만든 공연은 셋 다 빈 목록이다

### PerformanceStatusUpdateRequest / PerformanceStatusResponse

```json
{ "status": "DONE" }
```

```json
{ "id": 7, "status": "DONE" }
```

- `status`는 `PerformanceStatus`(`PLANNED|RECRUITING|DONE|CANCELED`)이고 필수다. 노션 값(`ENDED` 등)은 `400 VALIDATION_FAILED`
- 끝난 공연(`DONE`·`CANCELED`)은 바꿀 수 없다(`409 INVALID_STATE`). `DONE`·`CANCELED`로 바꾸면 소속 팀이 모두 해체된다(`api-spec.md` §8 ⊕)

### PerformanceTeamCreateRequest

`instrument`는 생성자 본인의 세션. Response는 TeamDetailResponse와 같은 형태다 (원본 페이지에 함께 기재됨).

- 제약은 `TeamCreateRequest`와 같다 — `name` 필수·50자, `description` 1,000자, `region` 선택, `genres` 5개까지, `instrument` 필수(2026-11-01, 이슈 #88)
- 응답은 `201` + `TeamDetailResponse`다. `performance`(소속 공연 요약)는 #130에서 팀 상세에 추가돼 이 응답에도 담긴다
- `PLANNED`·`RECRUITING` 공연에서만 만들 수 있다 — 끝난 공연은 `409 INVALID_STATE`

```json
{
  "name": "무명밴드",
  "description": "발라드 위주로 갑니다.",
  "region": "SEOUL",
  "genres": ["BALLAD", "ROCK"],
  "instrument": "BASS"
}
```

### SetlistResponse

공연 셋리스트 조회·넘기기·확정·확정 해제 공통 응답(2026-10-30, 이슈 #85). 이전 이름 `PerformanceSetlistResponse`.

```json
{
  "performanceId": 7,
  "confirmed": true,
  "setlist": [
    {
      "teamId": 5,
      "teamName": "무명밴드",
      "song": { "id": 88, "title": "Bohemian Rhapsody", "artist": "Queen" },
      "sortOrder": 1
    }
  ]
}
```

- `confirmed`는 `performances.setlist_confirmed_at`이 있는지다
- 확정 전(또는 확정 해제 뒤)에는 팀들이 넘긴 곡 전체(팀별·넘긴 순, `sortOrder`는 `null`), 확정 후에는 확정곡만(`sortOrder` 순)
- `setlist[]` 항목은 `SetlistItemResponse`(setlist 도메인)다. 팀이 해체돼도 `teamName`은 나온다 — 끝난 공연의 셋리스트를 남기기 때문이다

### SetlistSubmitRequest

셋리스트로 넘기기(`PUT /v1/performances/{performanceId}/setlist/teams/{teamId}`). 신규.

```json
{ "songIds": [88, 91] }
```

- 그 팀의 선택곡 안에서 고른다(전체도 가능). 그 팀이 이전에 넘긴 곡 전체를 바꾸고, 빈 목록이면 넘긴 곡을 모두 뺀다
- 선택곡에 없는 곡은 409 `SONG_NOT_SELECTED_BY_TEAM`, 같은 공연의 다른 팀이 이미 넘긴 곡은 409 `SONG_ALREADY_IN_SETLIST`(동시에 넘겨도 DB 유니크가 막아 같은 응답), 이미 확정된 셋리스트는 409 `INVALID_STATE`(확정과 동시에 넘겨도 공연 행 잠금으로 차례로 처리돼 같은 응답. 같은 공연에 두 팀이 동시에 넘기는 것만으로는 실패하지 않는다) — 2026-11-02, 이슈 #103 · 2026-11-03 PR #111 리뷰

### SetlistConfirmRequest

공연 셋리스트 확정(`PUT /v1/performances/{performanceId}/setlist`). 이전 이름 `PerformanceSetlistUpdateRequest`. 노션도 2026-11-02에 `SetlistUpdateRequest`에서 이 이름으로 맞췄다(`dto-naming.md` §9).

```json
{
  "items": [
    { "teamId": 5, "songId": 88, "sortOrder": 1 },
    { "teamId": 5, "songId": 91, "sortOrder": 2 },
    { "teamId": 9, "songId": 104, "sortOrder": 3 }
  ]
}
```

- 팀들이 넘긴 곡만 넣을 수 있다(아니면 409 `SONG_NOT_TEAM_CANDIDATE`). 요청에 없는 곡은 셋리스트에서 빠진다
- `items`는 비어 있을 수 없고, 같은 곡은 한 번만, `sortOrder`는 1부터 빈틈없이(1..n) 매긴다 — 어기면 400 `VALIDATION_FAILED`(2026-11-03, 이슈 #103). 빈 목록을 막는 이유는 실수 한 번에 넘겨진 곡이 전부 지워지기 때문이다
- 확정된 뒤에도 다시 수정·확정할 수 있다. 팀이 다시 넘기게 하려면 확정을 해제한다(아래)
- 끝났거나 취소된 공연(`DONE`·`CANCELED`)은 확정도 409 `INVALID_STATE`다 — 해제와 같은 이유(2026-11-02, 이슈 #103)

### 공연 셋리스트 확정 해제

`PATCH /v1/performances/{performanceId}/setlist/unconfirm` — **요청 바디가 없다.** 응답은 `SetlistResponse`(`confirmed`는 `false`). 2026-11-02 추가(PR #87 리뷰).

- 모임장만 한다. `performances.setlist_confirmed_at`을 NULL로, 셋리스트 행의 `sort_order`를 NULL로 되돌린다. 셋리스트에 남아 있던 곡은 그대로 두고, 팀은 다시 넘길 수 있다
- 확정되지 않은 셋리스트, 끝났거나 취소된 공연(`DONE`·`CANCELED`)은 409 `INVALID_STATE` — 끝난 공연의 셋리스트는 기록이다

## §9. 노래 (song) · 셋리스트 (setlist)

### PageResponse\<SongResponse\>

```json
{
  "id": 88,
  "title": "Bohemian Rhapsody",
  "artist": "Queen",
  "genre": "ROCK",
  "songKey": "Bb",
  "bpm": 72,
  "difficulty": [
    { "part": "VOCAL", "level": "ADVANCED" },
    { "part": "EL_GT", "level": "ADVANCED" },
    { "part": "DRUM", "level": "INTERMEDIATE" }
  ]
}
```

- `difficulty`는 파트별 난이도 배열이다(`SongDifficultyResponse(part, level)`). 정보가 없는 파트는 배열에서 빠진다. `part`는 `Instrument`, `level`은 `Level`(`BEGINNER`~`ADVANCED`). **2026-11-02에 객체(`{"VOCAL":"ADVANCED"}`)에서 노션대로 배열로 바꿨다**(이슈 #103) — DB(`songs.difficulty` JSON)는 그대로 객체이고 응답에서만 펼친다
- 쿼리 `keyword`는 제목·아티스트 부분 일치, `genre`는 장르 필터, `page`·`size`는 기본·최대 20

### SongSyncResponse (곡 수동 동기화 · 운영용)

`POST /v1/songs/sync`의 응답(2026-11-11 · 이슈 #162). AI 카탈로그를 처음부터 끝까지 한 번 받은 결과다. 노션 `API 초안`에는 없는 운영용 API다.

```json
{ "pages": 3, "created": 120, "updated": 880, "skipped": 2 }
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `pages` | int | AI 서버에서 받은 페이지 수 |
| `created` | int | 새로 만든 곡 수 |
| `updated` | int | 이미 있던 `externalId`라 받은 값으로 바꾼 곡 수 |
| `skipped` | int | 검증에 걸려 건너뛴 곡 수(이유는 서버 로그) |

### AiSongPage (AI 서버 응답 · 잠정)

백엔드가 AI 서버에서 받는 곡 카탈로그 한 페이지(`GET {AI}/v1/songs?cursor=&limit=`, 2026-11-06 · 이슈 #147). 프론트 API의 DTO가 아니라 `domain/song/client`에 둔다(`KakaoUserInfo`와 같은 자리). 동작은 `api-spec.md` §12. **AI 팀과 맞추기 전 잠정안이다.**

```json
{
  "songs": [
    {
      "externalId": "youtube:Lb3mQdm-UHs",
      "title": "Bohemian Rhapsody",
      "artist": "Queen",
      "genre": "ROCK",
      "songKey": "Bb",
      "bpm": 72,
      "difficulty": [
        { "part": "VOCAL", "level": "ADVANCED" },
        { "part": "DRUM", "level": "INTERMEDIATE" }
      ]
    }
  ],
  "nextCursor": "eyJpZCI6NTAwfQ"
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `songs` | List | O | 한 페이지 최대 `limit`(500)개. 없으면 응답 형식 위반으로 그 회차가 실패한다 |
| `nextCursor` | String | X | 다음 페이지 커서. `null`이면 마지막 페이지. 백엔드는 해석하지 않고 그대로 다음 요청에 넣는다 |
| `songs[].externalId` | String | O | AI 쪽 곡 id. 100자 이하. 이 값으로 같은 곡을 찾는다(`songs.external_id` 유일). **대소문자를 구분한다**. 앞뒤 공백이 있거나 한 페이지 안에서 겹치면 그 곡을 건너뛴다 |
| `songs[].title` | String | O | 200자 이하 |
| `songs[].artist` | String | O | 100자 이하 |
| `songs[].genre` | Enum | O | `Genre`(`ROCK` `INDIE` `PUNK` `METAL` `POP` `BALLAD` `JAZZ` `HIPHOP` `RNB` `ACOUSTIC` `ETC`) |
| `songs[].songKey` | String | X | 10자 이하. 빈 문자열·공백뿐이면 키 정보 없음(`null`)으로 저장하고, 앞뒤 공백은 지운다 |
| `songs[].bpm` | Integer | X | 양수 |
| `songs[].difficulty` | List | X | 곡 검색 응답과 같은 `[{part, level}]` 배열. `part`는 `Instrument`, `level`은 `Level`. 한 곡에 같은 파트가 두 번이면 그 곡을 건너뛴다. 없거나 빈 배열이면 난이도 정보 없음 |

- 필수 값이 빠지거나 길이를 넘는 곡은 그 곡만 건너뛰고 나머지는 저장한다
- DB(`songs.difficulty` JSON)에는 지금처럼 `{"VOCAL":"ADVANCED"}` 객체로 저장한다

### AiSongRecommendRequest · AiSongRecommendResponse (AI 서버 곡 추천)

백엔드가 AI 서버에 보내는 곡 추천 요청과 받는 응답(`POST {AI}/v1/recommendations/songs`, 2026-11-12 · 이슈 #171). 프론트 API의 DTO가 아니라 `domain/song/client`에 둔다. 동작은 `api-spec.md` §12. 형식은 AI 팀이 정했다(`moyeorock-ai` 커밋 `09d46a3`).

```json
{ "mode": "TEAM", "genres": ["ROCK"], "members": [ { "part": "BASS", "level": "NOVICE" } ], "limit": 20 }
```

```json
{ "songs": [ { "externalId": "local-abc", "score": 0.875 } ], "ranker": "content_rule" }
```

- `mode`는 `TEAM` 또는 `PERSONAL`(개인 추천, 이슈 #172에서 쓴다). 값은 우리 enum 그대로다(`part`=`Instrument`, `level`=`Level`, `genres`=`Genre`).
- `members`에는 실력을 아는 사람만 넣는다. `level`이 비면 AI가 422를 준다.
- 응답의 `score`가 비어 있어도 읽는다(그 항목은 버린다). 응답에 `songs` 자체가 없으면 계약 위반이라 `503`이다.

### AiRecommendRequest · AiRecommendResponse (AI 서버 팀원 추천 · 잠정)

백엔드가 AI 서버에 보내는 팀원 추천 요청과 받는 응답(`POST {AI}/v1/recommendations/members`, 2026-11-08 · 이슈 #150). 프론트 API의 DTO가 아니라 `domain/team/client`에 둔다. 동작은 `api-spec.md` §12. **AI 팀과 맞추기 전 잠정안이다.** 값은 ERD 컬럼과 우리 enum 그대로다.

```json
{
  "team": { "id": 5, "region": "SEOUL", "genres": ["ROCK", "POP"] },
  "instrument": "DRUM",
  "members": [ { "userId": 3, "instrument": "VOCAL", "level": "ADVANCED" } ],
  "candidates": [ { "userId": 30, "region": "SEOUL", "genres": ["ROCK"], "level": "INTERMEDIATE" } ]
}
```

```json
{ "candidates": [ { "userId": 30, "score": 0.87, "reasons": ["장르 일치: ROCK", "활동 지역 동일"] } ] }
```

- `members[].instrument`는 그 사람이 이 팀에서 맡은 세션(`team_members.instrument`), `members[].level`은 그 악기에서의 실력(`user_instruments.level`, 없으면 `null`)이다. `candidates[].level`은 요청한 `instrument`에서의 실력이다.
- 온보딩 전 사용자는 `region`·`genres`가 `null`일 수 있다. `team.region`도 `null`일 수 있다.
- 응답의 `score`·`reasons`가 비어 있어도 읽는다(점수가 없는 항목은 버리고, `reasons`가 없으면 빈 배열로 응답한다). 응답에 `candidates` 자체가 없으면 계약 위반이라 `503`이다.

### SongRecommendationResponse

```json
{
  "songs": [
    {
      "song": {
        "id": 88, "title": "Bohemian Rhapsody", "artist": "Queen",
        "genre": "ROCK", "songKey": "Bb", "bpm": 72,
        "difficulty": [{ "part": "VOCAL", "level": "ADVANCED" }]
      },
      "score": 0.91
    }
  ]
}
```

### TeamSongRecommendationResponse

```json
{
  "teamId": 5,
  "songs": [
    {
      "song": {
        "id": 88, "title": "Bohemian Rhapsody", "artist": "Queen",
        "genre": "ROCK", "songKey": "Bb", "bpm": 72,
        "difficulty": [{ "part": "VOCAL", "level": "ADVANCED" }]
      },
      "score": 0.87,
      "reasons": ["팀 장르 일치: 록", "보컬 실력 충족", "드럼 난이도 초과"],
      "selectedByOtherTeam": false
    }
  ]
}
```

- 추천 두 응답의 `song`은 곡 검색과 같은 전체 정보(`SongResponse`)이고, 선택곡·셋리스트 항목의 `song`은 축약형(`SongSummaryResponse` — `id`·`title`·`artist`)이다(2026-11-02, 이슈 #103). 추천 2개는 이슈 #103 구현 범위 밖이다
- 팀 곡 추천은 2026-11-12에 구현했다(이슈 #171). `songs`는 AI가 준 순서(점수 높은 순)이고 **최대 10곡**이다. AI가 고른 곡 중 우리 DB에 없는 곡은 빠지고, 고른 곡이 없으면 빈 배열이다
- `score`는 AI가 준 0~1 정렬용 점수이며 선호 확률이 아니다
- `reasons`는 **백엔드가 만든다**(AI는 주지 않는다). 화면에 그대로 보이므로 장르·세션은 프론트 표시명(`domainCodes.ts`)으로 쓴다. 팀 장르에 곡 장르가 있으면 `팀 장르 일치: {장르}`, 팀이 가진 세션마다 곡 난이도와 그 세션의 가장 높은 팀원 실력을 비교해 `{세션} 실력 충족` 또는 `{세션} 난이도 초과`. 곡에 그 세션 난이도 정보가 없거나 그 세션 팀원의 실력을 모르면 문구가 없다. 근거가 하나도 없으면 빈 배열이다(처음 예시의 `팀 장르 일치: ROCK`에서 표시명으로 바꿨다)
- `selectedByOtherTeam`은 같은 공연의 다른 팀이 이미 고른 곡인지다("다른 팀이 이미 선택했어요" 표시). 독립 팀이면 항상 `false`. 2026-10-30에 `alreadyInSetlist`에서 이름을 바꿨다(이슈 #85, 노션도 수정)

### TeamSongsSaveRequest

팀 선택곡 저장(`PUT /v1/teams/{teamId}/songs`). 이전 이름 `TeamSetlistUpdateRequest`.

```json
{ "songIds": [88, 91] }
```

- 기존 선택곡 전체를 바꾼다(빈 목록이면 전부 삭제). `sortOrder`·`progress`는 2026-10-30에 없앴다 — 순서는 공연 셋리스트에서만 정한다
- `songIds`는 필수이고 같은 곡이 두 번 있으면 400, 없는 곡이 섞이면 404 `SONG_NOT_FOUND`(2026-11-02, 이슈 #103)

### TeamSongsResponse

이전 이름 `TeamSetlistResponse`. 팀 선택곡 저장(`PUT`)과 조회(`GET /v1/teams/{teamId}/songs`, 2026-11-02 신규) 공통 응답이다.

```json
{
  "teamId": 5,
  "songs": [
    { "id": 21, "song": { "id": 88, "title": "Bohemian Rhapsody", "artist": "Queen" } },
    { "id": 22, "song": { "id": 91, "title": "Don't Stop Me Now", "artist": "Queen" } }
  ]
}
```

## §10. 알림 (notification) · 북마크 (bookmark) · 대시보드 (dashboard) · 파일 (file)

### NotificationsResponse

배지 숫자(`unreadCount`) 때문에 래퍼를 두고, 목록 자체는 `PageResponse<NotificationResponse>`로 페이징한다. 정렬은 최신순 고정이다(클라이언트 `sort`는 무시).

```json
{
  "unreadCount": 3,
  "notifications": {
    "content": [
      {
        "id": 501,
        "type": "TEAM_INVITE",
        "message": "무명밴드에서 드럼 세션으로 초대했습니다.",
        "targetType": "INVITATION",
        "targetId": 91,
        "isRead": false,
        "createdAt": "2026-08-11T16:30:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 3,
    "totalPages": 1,
    "last": true
  }
}
```

### NotificationReadRequest / NotificationReadResponse

`NotificationReadRequest`

```json
{ "ids": [501, 502] }
```

- `ids`는 선택이다. **null이거나 빈 배열이면 전체 읽음**이고, 요청 바디가 아예 없어도 전체 읽음이다.
- 최대 100개 — 명세에 없는 서버 정책이다. 초과하거나 `null` 원소가 있으면 `400 VALIDATION_FAILED`.
- 남의 알림 id·없는 id·이미 읽은 알림은 오류 없이 무시되고 `readCount`에 잡히지 않는다.

`NotificationReadResponse`

```json
{ "readCount": 3, "unreadCount": 0 }
```

`readCount`는 이번 요청으로 읽음 처리된 알림 수, `unreadCount`는 처리 후 남은 안읽은 알림 수다.

### BookmarkCreateRequest

```json
{ "targetType": "USER", "targetId": 30 }
```

`targetType`은 `TEAM`·`USER`·`SONG`이고 세 대상 모두 저장된다. 곡 `target`은 `name`=제목, 나머지는 `null`이다. `api-spec.md` §10 참고.

### BookmarkResponse / PageResponse\<BookmarkResponse\>

저장 추가(201)는 `BookmarkResponse` 하나, 목록은 `PageResponse<BookmarkResponse>`다.

```json
{
  "id": 21,
  "targetType": "USER",
  "targetId": 30,
  "target": {
    "id": 30, "name": "민서", "region": "GYEONGGI",
    "genres": ["JAZZ"], "imageUrl": "https://.../c.jpg"
  },
  "createdAt": "2026-08-10T12:00:00"
}
```

- `target`의 해당 없는 필드는 `null`이다(곡은 `region`이 없다). 사용자 대상은 `name`=닉네임, `imageUrl`=프로필 이미지이고 온보딩 전이면 `region`·`genres`가 없다. 팀 대상은 `name`=팀 이름이고 `imageUrl`은 `null`이다(팀에는 이미지가 없다).
- 대상이 삭제·탈퇴·해체돼 조회되지 않으면 **`target`이 `null`이다**. 항목은 목록에서 빠지지 않는다.

삭제(`DELETE /v1/bookmarks/{id}`)의 응답은 `DeleteResponse`다.

```json
{ "id": 21, "deleted": true }
```

### PresignedUrlCreateRequest

```json
{
  "domain": "PERFORMANCE_POSTER",
  "fileName": "poster.png",
  "contentType": "image/png"
}
```

### PresignedUrlResponse

```json
{
  "fileId": 101,
  "uploadUrl": "https://moyeorock.s3.ap-northeast-2.amazonaws.com/performance/uuid.png?X-Amz-...",
  "fileUrl": "https://cdn.moyeorock.com/performance/uuid.png",
  "expiresAt": "2026-08-11T16:10:00"
}
```

- `fileId`는 2026-09-30에 `DELETE /v1/files/{fileId}`를 부르려고 추가했다. 2026-11-11 삭제가 `DELETE /v1/files?fileUrl=`로 바뀌어(이슈 #164) **삭제에는 쓰지 않는다** — 이미 읽는 프론트 코드가 깨지지 않게 남겨 둔다. 삭제 응답 `DeleteResponse.id`와 같은 값이다
- `expiresAt`은 발급 후 **10분**이다(`file.storage.local.upload-url-expiry-seconds`). 지나면 업로드 PUT이 403 `UPLOAD_URL_EXPIRED`다(S3도 만료 후 403). 만료 전에는 같은 `uploadUrl`로 다시 올리면 덮어쓴다 — S3 presigned URL과 같은 동작이다
- 허용 형식·크기는 `domain`마다 다르다(노션 `Presigned URL 발급` 표, 2026-11-09 대조). 확장자와 `contentType` 중 하나라도 그 도메인 목록에 없으면 400 `UNSUPPORTED_FILE_TYPE`이다 — 확장자만 막으면 `contentType`으로 우회돼 서빙 응답 헤더에 그대로 실리기 때문이다

  | `domain` | 확장자 | `contentType` | 최대 크기 |
  |---|---|---|---|
  | `PROFILE_IMAGE` `GROUP_COVER` `PERFORMANCE_POSTER` | `jpg` `jpeg` `png` `webp` | `image/jpeg` `image/png` `image/webp` | 10MB |
  | `PRACTICE_AUDIO` | `mp3` `m4a` `wav` | `audio/mpeg` `audio/mp4` `audio/x-m4a` `audio/wav` `audio/x-wav` | 1.95MB(1,950,000바이트, AI 연습 피드백 API 한도 — 2026-11-11) |

- `PRACTICE_AUDIO`(개인 연습 녹음)는 **비공개**다. S3 `practice/`는 공개 읽기가 아니라서 `fileUrl`로 바로 열리지 않는다 — 연습 녹음 분석 요청의 `audioUrl`로 보내는 식별자로만 쓰고, 재생은 연습 녹음 상세가 주는 임시 URL로 한다(이슈 #154)
- 로컬 저장 중에는 `uploadUrl`·`fileUrl`이 S3·CDN이 아니라 이 서버를 가리킨다(`http://{host}/v1/files/local-upload/{fileKey}` · `/v1/files/local/{fileKey}`). S3 전환 시 위 예시 모양으로 바뀌며 **응답 구조는 그대로다**. 단 업로드 PUT의 `Authorization` 헤더 규칙이 바뀐다(로컬은 필수, S3는 금지) — `docs/specs/api-spec.md` §10 file "업로드 PUT 규칙" 참고

### DashboardResponse

```json
{
  "upcomingRehearsals": ["RehearsalSummary"],
  "openRecruitPosts": ["RecruitPostSummary"],
  "recommendedSongs": [
    { "id": 88, "title": "Bohemian Rhapsody", "artist": "Queen" }
  ],
  "unreadNotificationCount": 3
}
```

- 구현(2026-11-04, 이슈 #135): `upcomingRehearsals`는 내 팀 합주 중 오늘부터 14일 안에 끝나지 않은 것을 가까운 순 5개, `openRecruitPosts`는 모집 중(`OPEN`) 공고를 최신순 5개(개인화하지 않는다)다. 둘 다 항목은 각 도메인 응답(`RehearsalSummaryResponse`·`RecruitPostSummaryResponse`) 그대로다. 개수·기간은 서버 기본값이며 명세에 없어 정한 값이다 — Notion 반영 필요
- `recommendedSongs`는 AI 추천이 연결되기 전까지 **`null`**이다(빈 배열이 아님). 항목은 `SongSummaryResponse(id, title, artist)`다. 연결되면 배열로 바뀐다

## §11. 로그인 (auth)

2026-10-05에 `API 초안` 로그인 4건(2026-09-28 검토 반영본)과 대조했다.

### UserOAuth2CodeRequest

Body가 아니라 **Query 파라미터**다. 프론트가 카카오 로그인 리다이렉트로 받은 인가 코드를 그대로 넘긴다.

```
POST /v1/auth/kakao?code=abc123exampleAuthCode
```

`code`는 필수. 누락·공백이면 400 `VALIDATION_FAILED`. 인가 코드는 1회용이다.

### KakaoAuthResponse

카카오 인증 응답. 이 API는 계정을 만들지 않는다. `kakao_id`가 이미 있으면 `auth`에 로그인 토큰을, 없으면 `signupToken`과 `kakaoNickname`을 채운다.

기존 회원 (로그인 완료):

```json
{
  "isNewUser": false,
  "auth": {
    "accessToken": "string",
    "userId": 12,
    "nickname": "string",
    "onboardingCompleted": true
  }
}
```

신규 (가입 필요):

```json
{
  "isNewUser": true,
  "signupToken": "string",
  "kakaoNickname": "string"
}
```

| 필드 | Nullable | 설명 |
|---|---|---|
| `isNewUser` | X | true면 신규(가입 필요), false면 기존 회원(로그인 완료) |
| `auth` | O | 기존 회원일 때만. `AuthTokenResponse`. 신규면 null |
| `signupToken` | O | 신규일 때만. 서버가 서명한 가입용 토큰(10분 만료, `kakao_id`·카카오 닉네임 포함). `POST /v1/auth/kakao/signup`에 그대로 전달 |
| `kakaoNickname` | O | 신규일 때만. 카카오 프로필 닉네임(약관 화면 표시용) |

### KakaoSignupRequest

```json
{
  "signupToken": "string",
  "privacyAgreed": true
}
```

> 이 요청에서 계정이 생성된다. `signupToken`은 카카오 인증 응답 값 그대로(10분 만료). `privacyAgreed`는 true만 허용하고 false·누락이면 400. `privacy_agreed_at`은 서버 시각으로 저장한다.

### UserSignupRequest

```json
{
  "email": "string",
  "password": "string",
  "name": "string",
  "privacyAgreed": true
}
```

> `name`은 별도 컬럼이 아니라 **가입 시 `users.nickname`에 임시값으로 매핑**해 저장한다 (2026-08-19 팀 확인 — ERD에 name 컬럼이 없는 것은 의도). 정식 닉네임은 온보딩에서 재설정. 카카오 가입도 프로필 닉네임을 같은 방식으로 사용.
> **임시 닉네임 충돌 (2026-09-28 확정)**: `nickname`이 UNIQUE라 이미 쓰는 닉네임이면 서버가 `_`+영숫자 4자를 붙여 저장한다(예: `서준_a3f9`, 20자 이내). 중복 때문에 가입이 실패하지 않는다.
> **비밀번호 규칙 (2026-09-28 확정)**: 8~64자, 영문·숫자·특수문자를 각각 1자 이상 포함, 공백 불가. 위반 시 400. 가입에서만 검사한다.
> `privacyAgreed`는 true만 허용하고 false·누락이면 400. 서버가 동의 시각을 `privacy_agreed_at`에 저장한다.

### UserLoginRequest

```json
{
  "email": "string",
  "password": "string"
}
```

> 로그인에서는 비밀번호 규칙을 검사하지 않고(빈 값만 400) 일치 여부만 본다.

### AuthTokenResponse

카카오 가입 완료·이메일 가입·이메일 로그인이 공용으로 쓰고, `KakaoAuthResponse.auth`에도 담긴다. 리프레시 토큰은 없다.

```json
{
  "accessToken": "string",
  "userId": 12,
  "nickname": "string",
  "onboardingCompleted": false
}
```

> 가입 응답의 `nickname`은 임시 닉네임이고 `onboardingCompleted`는 항상 false다. 로그인에서는 `onboardingCompleted`가 false면 온보딩 화면, true면 대시보드로 보낸다.

---

## §13. 연습 (practice)

2026-11-09 신규(이슈 #155). Notion `API 초안` 도메인 "연습"과 대조했다.

### PracticeRecordingCreateRequest

```json
{
  "title": "11/9 보컬 연습 - 너의 의미",
  "part": "VOCAL",
  "songId": 41,
  "audioUrl": "https://moyeorock-dev-files.s3.ap-northeast-2.amazonaws.com/practice/550e8400-e29b-41d4-a716-446655440000.m4a"
}
```

- `title` 필수, 100자 이하
- `part` 필수. 녹음한 세션(`VOCAL` `EL_GT` `AC_GT` `BASS` `DRUM` `KEY`) — 혼자 연습한 세션 하나의 녹음이다. AI에 세션 힌트로 넘긴다
- `songId` 필수. 연습한 곡(곡 검색 API의 `id`). 없는 곡이면 404 `SONG_NOT_FOUND`. AI가 이 곡의 원곡과 녹음을 맞춰 본다 — 시범 지원 곡이 아니면 분석이 `FAILED`가 된다(2026-11-11 추가)
- `audioUrl` 필수. 업로드 API(`domain: PRACTICE_AUDIO`)가 준 `fileUrl` 그대로. 내가 올린 연습 녹음 파일이어야 하고, 실제로 업로드돼 있어야 하며 1.95MB 이하여야 한다(녹음 길이는 3~60초) — 아니면 400 `VALIDATION_FAILED`(`message`에 `"audioUrl: …"` 사유)

### PracticeRecordingSummaryResponse

분석 요청·다시 요청의 응답(202)과 내 연습 녹음 목록(`PageResponse`)의 항목.

```json
{
  "id": 12,
  "title": "11/9 보컬 연습 - 너의 의미",
  "part": "VOCAL",
  "status": "SUBMITTED",
  "createdAt": "2026-11-09T14:30:00",
  "analyzedAt": null
}
```

- `status`: `SUBMITTED`(전송 완료) · `ANALYZING`(분석 중) · `DONE`(분석 완료) · `FAILED`(분석 실패)
- `createdAt`: 분석을 요청한 시각. 다시 요청해도 바뀌지 않는다. 목록은 이 값의 최근 순
- `analyzedAt`: 분석이 끝난 시각. `DONE`이 아니면 `null`

### PracticeRecordingDetailResponse

```json
{
  "id": 12,
  "title": "11/9 보컬 연습 - 너의 의미",
  "part": "VOCAL",
  "audioUrl": "https://moyeorock-dev-files.s3.ap-northeast-2.amazonaws.com/practice/550e8400-e29b-41d4-a716-446655440000.m4a?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Expires=1800&...",
  "status": "DONE",
  "failureReason": null,
  "createdAt": "2026-11-09T14:30:00",
  "analyzedAt": "2026-11-09T14:33:12",
  "feedbacks": [
    { "startSec": 80, "endSec": 95, "part": "VOCAL", "content": "음정이 2도 플랫되었습니다" },
    { "startSec": 4803, "endSec": 4865, "part": "VOCAL", "content": "고음 구간에서 박자가 조금 늦습니다" }
  ]
}
```

- `audioUrl`: 재생용 **임시 URL**(30분). 조회할 때마다 새로 만들어 값이 달라진다 — 녹음은 비공개라 DB에는 URL이 아니라 스토리지 key가 있다. 로컬 저장(개발용)에서는 서빙 경로(`/v1/files/local/practice/...`)가 나간다
- `failureReason`: `FAILED`일 때만 값. 화면에 그대로 보여 주는 문장이다(예: "서버 재시작으로 분석이 중단되었습니다. 다시 요청해 주세요.")
- `feedbacks`: `DONE`이 아니면 빈 배열. 구간 시작 순. `startSec`·`endSec`는 초(1:20:03 → 4803), `part`는 공용 `Instrument`(`VOCAL` `EL_GT` `AC_GT` `BASS` `DRUM` `KEY`)
- 결과 형식(구간·세션·내용)은 AI 파트와 확정 전이다. 지금은 가짜 AI가 녹음한 세션(`part`)으로 "(테스트) …" 문장 3줄을 준다

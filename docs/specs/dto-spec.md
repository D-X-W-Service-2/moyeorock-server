# DTO 명세 — API 명세서 v0 기준

- **원본**: Notion `API 명세서 v0` DTO 하위 페이지
- 절 구성은 `api-spec.md`와 동일 (§1~§11 — 두 파일에서만 쓰는 좌표다). 네이밍 규칙은 `docs/dto-naming.md` 참조.
- `PageResponse<T>` 페이지는 **item(T)의 형태만** 정의한다. 페이지 래퍼(content, page, size, totalElements 등)는 `global/common/dto/PageResponse` 공통 정의를 따른다.

## 공용 참조 객체 — 확정 (2026-08-27, 1팀)

원본 JSON 예시가 문자열로만 참조하던 객체들. `docs/dto-naming.md`의 정식 이름으로 매핑하고 필드를 확정했다.
패키지는 `global/common/dto`가 아니라 **각 소유 도메인의 `dto/response/`다** — 응답 DTO는 도메인 간 import가 허용된다 (`docs/dto-naming.md` §0 패키지 규칙). `global/common/dto`에는 봉투 5종만 둔다.

| 원본 표기 | 정식 이름 | 소유 | 필드 | 상태 |
|---|---|---|---|---|
| `UserSummary` | `UserSummaryResponse` | user (1팀) | `(Long id, String nickname, String profileImage)` | **제안** — 재사용 1위라 최소로. 세션·지역이 필요한 곳(팀원·추천)은 감싸는 DTO가 이미 별도 필드로 가짐 |
| `TeamSummary` | `TeamSummaryResponse` | team (2팀) | `(Long id, String name, Region region, List<Genre> genres, TeamStatus status, int memberCount)` | **제안** — 2팀 확인 필요 |
| `MemberItem` | `TeamMemberResponse` | team (2팀) | `(UserSummaryResponse user, TeamRole role, Instrument instrument, MemberStatus status, LocalDateTime joinedAt)` | 확정 (`dto-naming.md` §3) |
| `RehearsalItem` · `RehearsalSummary` | `RehearsalSummaryResponse` | rehearsal (2팀) | `(Long id, Long teamId, String teamName, String title, LocalDateTime startsAt, LocalDateTime endsAt, String place)` | **제안** — `dto-naming.md` §4 "캘린더 항목, teamId·teamName 포함"을 구체화 |
| `RecruitPostSummary` | `RecruitPostSummaryResponse` | recruit (3팀) | 본문 §4 JSON 예시 그대로 (`target`은 자체 축약형) | 확정 (`dto-naming.md` §5) |

`PageResponse<T>` 래퍼 필드도 `docs/dto-naming.md` §0에 이미 확정되어 있다: `{content, page, size, totalElements, totalPages, last}` + `PageResponse.from(Page<T>)` 변환. 이 파일 상단의 "래퍼는 공통 정의를 따른다" 문구가 가리키는 정의가 이것이다.

### 왜 지금(착수 전) 확정하는가

DTO 필드는 원칙적으로 소유 팀이 착수할 때 정하지만, 아래 3개는 **팀 경계를 넘는 의존의 시작점**이라 착수 전에 정하지 않으면 서로를 블로킹한다.

1. **`UserSummaryResponse` (1팀 소유)** — 재사용 1위. team·group·join·invitation·recruit가 전부 참조하므로(`docs/dto-naming.md` §12), 1팀이 확정을 미루면 **2·3·4팀 전원이 대기**하게 된다. dto-naming.md 착수 순서에서 공통 5종·enums 다음 3순위로 못 박힌 이유.
2. **`TeamSummaryResponse` (2팀 소유)** — 방향이 반대인 케이스. 1팀이 만들 `GET /v0/users/me/activities`(`UserActivityResponse.TeamActivity`)와 `GET /v0/users/{id}`(`UserProfileResponse.activities`)가 이걸 참조하므로(`docs/dto-naming.md` §0 예시 코드), 미확정이면 **1팀 user API 2개가 완성 불가**. 소유는 2팀이지만 1팀이 기다리는 쪽이라 착수 전 합의가 필요해 위 표에 제안을 올렸다.
3. **`PageResponse<T>` (1팀 소유, global/common)** — 모든 팀의 목록 API가 import하는 클래스. 확정이 늦으면 각 팀이 임시 포장을 만들었다가 갈아엎게 된다. 필드는 이미 dto-naming.md §0에 있으므로 구현만 선행하면 된다.

나머지 요약 객체(`RehearsalSummaryResponse` 등)는 1팀이 의존하지 않으므로 소유 팀 착수 시점에 확정해도 늦지 않다 — 단, 재사용 상위 5개는 변경 시 공유 필수 규칙(`docs/dto-naming.md` §12)을 따른다.

## 빈 DTO (Notion 페이지는 있으나 내용 미작성)

`BookmarkResponse` · `JoinRequestResponse` · `PageResponse<JoinRequestResponse>` · `PageResponse<InvitationResponse>` · `PageResponse<TeamsummaryResponse>` · `PageResponse<UserSummaryResponse>` · `NotificationReadRequest` · `NotificationReadResponse` · `RehearsalUpdateRequest`

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
    { "id": 3, "instrument": "BASS", "customInstrument": null, "level": "INTERMEDIATE" },
    { "id": 4, "instrument": "ETC", "customInstrument": "트럼펫", "level": "BEGINNER" }
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

### OnboardingCreateRequest

```json
{
  "nickname": "서준",
  "region": "SEOUL",
  "genres": ["ROCK", "INDIE"],
  "instruments": [
    { "instrument": "BASS", "level": "INTERMEDIATE" },
    { "instrument": "ETC", "customInstrument": "트럼펫", "level": "BEGINNER" }
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

`PUT /v0/users/me/instruments`의 응답이다 (`docs/dto-naming.md` §2).

```json
{
  "instruments": [
    { "id": 8, "instrument": "BASS", "customInstrument": null, "level": "ADVANCED" },
    { "id": 9, "instrument": "KEY", "customInstrument": null, "level": "NOVICE" }
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
  "setlist": [
    {
      "id": 21,
      "song": { "id": 88, "title": "Bohemian Rhapsody", "artist": "Queen" },
      "sortOrder": 1,
      "progress": "SELECTED"
    }
  ],
  "myRole": "LEADER",
  "createdAt": "2026-08-01T10:00:00"
}
```

### TeamUpdateRequest

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

### TeamMemberRemoveResponse

```json
{ "teamId": 5, "userId": 30, "status": "REMOVED" }
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

## §3. 합주 (rehearsal)

### RehearsalCreateRequest

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

**미작성** — RehearsalCreateRequest와 동일 구성으로 추정.

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
  "target": "TeamSummary",
  "author": "UserSummary",
  "title": "베이스 1명 구합니다",
  "body": "주 1회 홍대에서 합주합니다.",
  "wantedSlots": [
    { "instrument": "BASS", "count": 1, "appliedCount": 3 }
  ],
  "region": "SEOUL",
  "status": "OPEN",
  "canEdit": false,
  "myJoinRequest": { "id": 77, "status": "PENDING" },
  "createdAt": "2026-08-11T15:00:00"
}
```

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

**미작성.**

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

### PageResponse\<InvitationResponse\>

**미작성** (보낸/받은 목록용 페이지 2개 모두 비어 있음).

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
      "status": "PLANNED"
    }
  ],
  "createdAt": "2026-07-10T12:00:00"
}
```

### PageResponse\<\> (구 GroupMembersResponse)

`GET /v0/groups/{id}/members`의 응답. Notion 원본 페이지가 2026-08-22에 `GroupMembersResponse` → `PageResponse<>`로 개명됨 — 모임원 목록을 페이지네이션 응답으로 바꾸려는 의도로 추정되나 제네릭(아이템 타입 이름)이 비어 있어 확정 필요. 아래 JSON은 개명 전과 동일한 단일 멤버(아이템) 형태다.

```json
{
  "user": "UserSummary",
  "role": "OWNER",
  "status": "ACTIVE",
  "joinedAt": "2026-07-10T12:00:00"
}
```

### GroupMemberUpdateRequest

```json
{ "role": "OWNER" }
```

### GroupMemberRemoveResponse

```json
{ "groupId": 2, "userId": 30, "status": "BANNED" }
```

### NoticeCreateRequest / NoticeUpdateRequest

두 DTO의 원본 예시가 동일하다.

```json
{
  "title": "3월 정기공연 안내",
  "body": "3월 22일 롤링홀에서 정기공연을 진행합니다.",
  "isPinned": true
}
```

### PageResponse\<NoticeResponse\>

```json
{
  "id": 8,
  "groupId": 2,
  "author": "UserSummary",
  "title": "3월 정기공연 안내",
  "body": "3월 22일 롤링홀에서 정기공연을 진행합니다. 참가 신청은...",
  "isPinned": true,
  "createdAt": "2026-08-01T09:00:00",
  "updatedAt": "2026-08-02T11:00:00"
}
```

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

### PageResponse\<PerformanceSummaryResponse\>

```json
{
  "id": 7,
  "title": "2026 정기공연",
  "performedAt": "2026-11-22T18:00:00",
  "venue": "홍대 롤링홀",
  "posterImage": "https://.../poster.jpg",
  "status": "PLANNED",
  "teamCount": 5
}
```

### PerformanceDetailResponse

`setlist`는 확정된 것만 담는다 (원본 메모).

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
  "myTeamId": 5,
  "createdAt": "2026-08-01T10:00:00"
}
```

### PerformanceStatusUpdateRequest / PerformanceStatusResponse

```json
{ "status": "DONE" }
```

```json
{ "id": 7, "status": "DONE" }
```

### PerformanceTeamCreateRequest

`instrument`는 생성자 본인의 세션. Response는 TeamDetailResponse와 같은 형태다 (원본 페이지에 함께 기재됨).

```json
{
  "name": "무명밴드",
  "description": "발라드 위주로 갑니다.",
  "region": "SEOUL",
  "genres": ["BALLAD", "ROCK"],
  "instrument": "BASS"
}
```

### PerformanceSetlistUpdateRequest

```json
{
  "items": [
    { "teamId": 5, "songId": 88, "sortOrder": 1 },
    { "teamId": 5, "songId": 91, "sortOrder": 2 },
    { "teamId": 9, "songId": 104, "sortOrder": 3 }
  ]
}
```

### PerformanceSetlistResponse

```json
{
  "performanceId": 7,
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
  "difficulty": { "VOCAL": "ADVANCED", "EL_GT": "ADVANCED", "DRUM": "INTERMEDIATE" }
}
```

### SongRecommendationResponse

```json
{
  "songs": [
    {
      "song": {
        "id": 88, "title": "Bohemian Rhapsody", "artist": "Queen",
        "genre": "ROCK", "songKey": "Bb", "bpm": 72,
        "difficulty": { "VOCAL": "ADVANCED" }
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
        "difficulty": { "VOCAL": "ADVANCED" }
      },
      "score": 0.87,
      "reasons": ["팀 장르 일치: ROCK", "보컬 실력 충족", "드럼 난이도 초과"],
      "alreadyInSetlist": false
    }
  ]
}
```

### TeamSetlistUpdateRequest

```json
{
  "items": [
    { "songId": 88, "sortOrder": 1, "progress": "PRACTICING" },
    { "songId": 91, "sortOrder": 2, "progress": "CANDIDATE" }
  ]
}
```

### TeamSetlistResponse

```json
{
  "teamId": 5,
  "setlist": [
    {
      "id": 21,
      "song": { "id": 88, "title": "Bohemian Rhapsody", "artist": "Queen" },
      "sortOrder": 1,
      "progress": "PRACTICING"
    }
  ]
}
```

## §10. 알림 (notification) · 북마크 (bookmark) · 대시보드 (dashboard) · 파일 (file)

### NotificationsResponse

```json
{
  "unreadCount": 3,
  "notifications": [
    {
      "id": 501,
      "type": "TEAM_INVITE",
      "message": "무명밴드에서 드럼 세션으로 초대했습니다.",
      "targetType": "INVITATION",
      "targetId": 91,
      "isRead": false,
      "createdAt": "2026-08-11T16:30:00"
    }
  ]
}
```

### NotificationReadRequest / NotificationReadResponse

**미작성** — 읽음 처리할 알림 id 목록을 담을 것으로 추정.

### BookmarkCreateRequest

```json
{ "targetType": "TEAM", "targetId": 5 }
```

### PageResponse\<BookmarkResponse\>

```json
{
  "id": 301,
  "targetType": "TEAM",
  "targetId": 5,
  "target": {
    "id": 5, "name": "무명밴드", "region": "SEOUL",
    "genres": ["ROCK"], "imageUrl": null
  },
  "createdAt": "2026-08-05T14:00:00"
}
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
  "uploadUrl": "https://moyeorock.s3.ap-northeast-2.amazonaws.com/performance/uuid.png?X-Amz-...",
  "fileUrl": "https://cdn.moyeorock.com/performance/uuid.png",
  "expiresAt": "2026-08-11T16:10:00"
}
```

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

## §11. 로그인 (auth)

### UserOAuth2CodeRequest

Body가 아니라 **Query 파라미터**다.

```
http://.../login/oauth2/code/kakao?code=abc123exampleAuthCode
```

### UserSignupRequest

```json
{
  "email": "string",
  "password": "string",
  "name": "string"
}
```

> `name`은 별도 컬럼이 아니라 **가입 시 `users.nickname`에 임시값으로 매핑**해 저장한다 (2026-08-19 팀 확인 — ERD에 name 컬럼이 없는 것은 의도). 정식 닉네임은 온보딩에서 재설정. 카카오 로그인도 프로필 name을 같은 방식으로 사용.
> 미결: `nickname` UNIQUE라 동명 가입자 충돌 시 처리(임시 닉네임 suffix 등) 확정 필요.

### UserLoginRequest

```json
{
  "email": "string",
  "password": "string"
}
```

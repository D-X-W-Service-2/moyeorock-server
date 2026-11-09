# moyeorock DB 테이블 명세 — ERD v3
대상: MySQL 8.0 · 18 테이블 · PK `BIGINT AUTO_INCREMENT`
기준: `band_erd_v3_mysql.drawio` 에서 자동 생성
표기: 타입 칸에 `NOT NULL`만 명시한다. 표기 없으면 NULL 허용.

## 변경 프로세스

스키마 변경은 임의로 하지 않는다 (CLAUDE.md 절대 규칙 5). 변경이 필요하면:

1. **변경 계획 md 작성** — 대상 테이블·컬럼, 변경 이유, 영향받는 도메인·팀, 필요한 마이그레이션을 적는다
2. **슬랙에 공유** — 특히 여러 팀이 쓰는 테이블(`users` `teams` `join_requests` 등)은 영향받는 팀의 확인을 받는다
3. **각자 맡은 부분을 직접 수정** — 합의 후 담당 팀이 이 문서의 해당 테이블 절을 직접 갱신하고, 같은 PR에서 엔티티·마이그레이션을 반영한다

ERD는 별도 스냅샷을 만들지 않는다. 구현 시 **이 문서를 노션 테이블 명세서와 직접 크로스체크**하고, 어긋나는 부분을 발견하면 슬랙에 알린다.

---

## 회원 관리

### 1. `users`

사용자

| 키 | 컬럼 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `BIGINT AUTO_INCREMENT` |  |
| UK | `email` | `VARCHAR(255)` | 유일 · 소셜 전용은 NULL |
|  | `password_hash` | `VARCHAR(255)` |  |
| UK | `kakao_id` | `VARCHAR(64)` | 유일 · 1.2 |
| UK | `nickname` | `VARCHAR(20) NOT NULL` | 유일 |
|  | `region` | `VARCHAR(50)` |  |
|  | `genres` | `JSON` | 선호 장르 3.1 |
|  | `bio` | `TEXT` |  |
|  | `profile_image` | `VARCHAR(500)` |  |
|  | `platform_role` | `VARCHAR(10) NOT NULL` | USER\|SUPER · 2.2 |
|  | `status` | `VARCHAR(20) NOT NULL` | ACTIVE\|WITHDRAWN\|SUSPENDED |
|  | `is_recommendable` | `TINYINT(1) DEFAULT 1` | 9.1 |
| ＋ | `is_activity_public` | `TINYINT(1) DEFAULT 1` | 9.2 |
| ＋ | `privacy_agreed_at` | `DATETIME(6) NOT NULL` | 2.3 |
| ＋ | `onboarding_completed_at` | `DATETIME(6)` |  |
|  | `withdrawn_at` | `DATETIME(6)` | 1.1 |
|  | `created_at` | `DATETIME(6)` |  |
| ＋ | `updated_at` | `DATETIME(6)` |  |


---

## 온보딩

### 2. `user_instruments`

연주 세션 + 실력

| 키 | 컬럼 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `BIGINT AUTO_INCREMENT` |  |
| FK | `user_id` | `BIGINT` | → users |
|  | `instrument` | `VARCHAR(20) NOT NULL` | VOCAL\|EL_GT\|AC_GT\|BASS\|DRUM\|KEY |
|  | `level` | `VARCHAR(20) NOT NULL` | 3.3 BEGINNER\|NOVICE\|INTERMEDIATE\|ADVANCED |

- **유니크** `(user_id, instrument)`

> 악기 `ETC`(직접 입력) 및 `custom_instrument` 컬럼은 2026-09-28 대면 회의에서 폐지했다. 유니크도 `(user_id, instrument)`로 되돌렸다.


---

## 마이페이지

### 3. `bookmarks`

저장 목록

| 키 | 컬럼 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `BIGINT AUTO_INCREMENT` |  |
|  | `user_id` | `BIGINT` | → users · 북마크한 사람 (참조는 `Long`, DB FK 없음 — CLAUDE.md 절대 규칙 6) |
|  | `target_team_id` | `BIGINT` | → teams · 셋 중 하나만 값을 가짐(exclusive-arc), 검증은 애플리케이션에서 |
|  | `target_user_id` | `BIGINT` | → users · 대상 사람 · 셋 중 하나만 값을 가짐(exclusive-arc), 검증은 애플리케이션에서 |
|  | `target_song_id` | `BIGINT` | → songs · 셋 중 하나만 값을 가짐(exclusive-arc), 검증은 애플리케이션에서 |
|  | `created_at` | `DATETIME(6)` |  |

- **유니크** `(user_id, target_team_id)` `(user_id, target_user_id)` `(user_id, target_song_id)` — MySQL 유니크 인덱스는 NULL끼리 서로 다르게 취급하므로 대상 타입별로 정확히 중복만 막는다 (UNIQUE는 FK가 아니므로 절대 규칙 6과 무관 — stream-server도 UNIQUE는 그대로 씀)

> 기존 `target_type`+`target_id`(다형성) → exclusive-arc(대상 타입별 컬럼 분리)로 전환(2026-09-16). FK·CHECK 제약은 걸지 않는다 — `docs/conventions/flyway-migration.md`·CLAUDE.md 절대 규칙 6(PR #29) 기준. 근거: `docs/plans/schema-cleanup-adoption-review.md` §③


---

## 동아리

### 4. `groups_`

모임 · 테이블명은 `groups`가 아니라 `groups_`다 (`GROUPS`는 MySQL 8.0.2+ 예약어라 `ddl-auto: update` 환경에서 충돌 위험, `docs/plans/schema-cleanup-adoption-review.md` 참고)

| 키 | 컬럼 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `BIGINT AUTO_INCREMENT` |  |
|  | `name` | `VARCHAR(50) NOT NULL` |  |
|  | `description` | `TEXT` |  |
|  | `type` | `VARCHAR(10)` | REGULAR\|PROJECT |
|  | `region` | `VARCHAR(50)` |  |
|  | `cover_image` | `VARCHAR(500)` |  |
|  | `status` | `VARCHAR(10) NOT NULL` | ACTIVE\|DISBANDED (기본값 ACTIVE) |
|  | `created_at` | `DATETIME(6)` |  |
| ＋ | `updated_at` | `DATETIME(6)` |  |

> 2026-10-29 `status` 추가(이슈 #80) — 모임 해체(`PATCH /v1/groups/{groupId}/status`)는 행을 지우지 않는 soft delete다(`architecture.md` §6). 해체 시 `group_members`·`group_notices`는 그대로 두며, 모임을 거쳐 들어오는 조회가 `DISBANDED`를 걸러 "없는 모임"(404)으로 본다. 모임을 거치지 않는 `recruit_posts`는 대상이 이 모임인 `OPEN` 공고를 같은 트랜잭션에서 `CLOSED`로 바꾼다(행은 지우지 않는다).

### 5. `group_members`

모임원 + 권한

| 키 | 컬럼 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `BIGINT AUTO_INCREMENT` |  |
| FK | `group_id` | `BIGINT` | → groups_ |
| FK | `user_id` | `BIGINT` | → users |
|  | `role` | `VARCHAR(10)` | OWNER\|MANAGER\|MEMBER |
|  | `status` | `VARCHAR(10)` | ACTIVE\|LEFT\|BANNED |
|  | `joined_at` | `DATETIME(6)` |  |

- **유니크** `(group_id, user_id)`
- **인덱스** `(user_id)` — 2026-10-29 추가(이슈 #80). 내 모임 목록이 `user_id`로 좁히는데 위 유니크는 `group_id`가 앞이라 탈 수 없다
- `MANAGER`는 2026-10-05에 값만 추가했다. **지금은 권한이 없고 `MEMBER`와 동일하게 취급한다** — 임원진 전용 동작이 필요해지는 시점에 권한을 붙인다. 컬럼 타입(`VARCHAR(10)`)이 그대로라 마이그레이션은 필요 없다

### 6. `group_notices`

공지사항 (7.4)  ← posts 개명

| 키 | 컬럼 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `BIGINT AUTO_INCREMENT` |  |
| FK | `group_id` | `BIGINT` | → groups_ |
| ＋ | `author_id` | `BIGINT` | → users |
|  | `title` | `VARCHAR(100)` |  |
|  | `body` | `TEXT` |  |
|  | `is_pinned` | `TINYINT(1) DEFAULT 0` |  |
|  | `created_at` | `DATETIME(6)` |  |
| ＋ | `updated_at` | `DATETIME(6)` |  |

- **인덱스** `(group_id, is_pinned, created_at)` ← 공지 목록·고정 필터·홈 미리보기가 모두 "모임으로 좁히고 → 고정 여부로 필터·정렬 → 최신순 → LIMIT" 모양이라 정렬까지 인덱스로 처리한다. V2에서 빠져 있어 2026-10-07 `V6__add_index_to_group_notices.sql`로 추가했다(처음엔 V4였으나 main의 V4·V5와 겹쳐 머지 때 옮겼다)

---

## 공용

### 7. `notifications`

알림

| 키 | 컬럼 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `BIGINT AUTO_INCREMENT` |  |
| FK | `user_id` | `BIGINT` | → users |
|  | `type` | `VARCHAR(30)` | TEAM_APPLY\|TEAM_INVITE\|GROUP_INVITE\|JOIN_REQUEST_APPROVED\|JOIN_REQUEST_REJECTED\|INVITATION_ACCEPTED\|INVITATION_DECLINED\|TEAM_MEMBER_REMOVED\|GROUP_MEMBER_REMOVED\|NOTICE_CREATED\|PERFORMANCE_CREATED\|RECRUIT_POST_APPLIED (`NotificationType`) |
|  | `message` | `VARCHAR(255)` |  |
| ＋ | `target_type` | `VARCHAR(20)` | 이동 대상 — TEAM\|GROUP\|PERFORMANCE\|NOTICE\|JOIN_REQUEST\|INVITATION (`NotificationTargetType`) |
| ＋ | `target_id` | `BIGINT` |  |
|  | `is_read` | `TINYINT(1) DEFAULT 0` |  |
|  | `created_at` | `DATETIME(6)` |  |

- **인덱스** `(user_id, is_read, created_at)`

### 8. `songs`

곡 마스터 (6.2.1)

| 키 | 컬럼 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `BIGINT AUTO_INCREMENT` |  |
|  | `title` | `VARCHAR(200)` |  |
|  | `artist` | `VARCHAR(100)` |  |
|  | `genre` | `VARCHAR(30)` |  |
|  | `song_key` | `VARCHAR(10)` |  |
|  | `bpm` | `INT` |  |
|  | `difficulty` | `JSON` | 세션별 난이도 |
| UK | `external_id` | `VARCHAR(100)` | 유일 |
| ＋ | `created_at` | `DATETIME(6)` |  |


---

## 공연

### 9. `performances`

공연

| 키 | 컬럼 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `BIGINT AUTO_INCREMENT` |  |
| FK | `group_id` | `BIGINT NOT NULL` | → groups_ |
|  | `title` | `VARCHAR(100)` |  |
|  | `description` | `TEXT` |  |
|  | `performed_at` | `DATETIME(6)` |  |
|  | `venue` | `VARCHAR(100)` | 필수 — 애플리케이션에서 검증, 컬럼은 NULL 허용 유지. 7.1.1의 "필수 X"는 `API 초안`(2026-10-08)에서 필수로 바뀌었다 |
|  | `poster_image` | `VARCHAR(500)` | 7.4 |
|  | `status` | `VARCHAR(20)` | PLANNED\|RECRUITING\|DONE\|CANCELED |
| FK | `created_by` | `BIGINT` | → users |
| ＋ | `setlist_confirmed_at` | `DATETIME(6)` | 셋리스트 확정 시각. NULL이면 미확정 — 확정되면 팀이 곡을 더 넘길 수 없다(2026-10-30, 이슈 #85). 모임장이 확정을 해제하면 다시 NULL(2026-11-02) |
|  | `created_at` | `DATETIME(6)` |  |
| ＋ | `updated_at` | `DATETIME(6)` |  |

- **인덱스** `(group_id, performed_at)` ← 모임 공연 목록(`performed_at` 내림차순) 조회용 (2026-10-30, V12)


---

## 모집 공고

### 10. `recruit_posts`

팀/모임 모집 (5.1, 5.2)

| 키 | 컬럼 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `BIGINT AUTO_INCREMENT` |  |
|  | `target_team_id` | `BIGINT` | → teams · 둘 중 하나만 값을 가짐(exclusive-arc), 검증은 애플리케이션에서 |
|  | `target_group_id` | `BIGINT` | → groups_ · 둘 중 하나만 값을 가짐(exclusive-arc), 검증은 애플리케이션에서 |
|  | `author_id` | `BIGINT` | → users |
|  | `title` | `VARCHAR(100)` |  |
|  | `body` | `TEXT` |  |
|  | `wanted_slots` | `JSON` | 세션별 인원 |
|  | `region` | `VARCHAR(50) NOT NULL` | 팀/모임 region과 무관 · 작성자가 작성·수정 시 직접 입력하는 독립 값 (동기화 불필요, `docs/plans/recruit-post-region-sync.md`) |
|  | `status` | `VARCHAR(10)` | OPEN\|CLOSED |
|  | `created_at` | `DATETIME(6)` |  |
| ＋ | `updated_at` | `DATETIME(6)` |  |

- **인덱스** `(status, region, created_at)`, `(target_team_id)`, `(target_group_id)`

> 2026-10-08 변경: `region` NULL 허용 → NOT NULL. 명세(Notion `API 초안` 공고 작성 Required)와 요청 DTO는 이미 필수였고 스키마만 nullable이라 어긋나 있었다 — NULL 공고는 `region` 필터에 잡히지 않고 응답에서 키가 빠진다(PR #71 리뷰). 사유·영향: `docs/plans/recruit-post-region-not-null.md`.

> 기존 `target_type`+`target_id`(다형성) → exclusive-arc(대상 타입별 컬럼 분리)로 전환(2026-09-16). FK·CHECK 제약은 걸지 않는다 — 참조 컬럼은 전부 `Long` 매핑, DB 제약 없음(CLAUDE.md 절대 규칙 6, PR #29). 근거: `docs/plans/schema-cleanup-adoption-review.md` §③

### 11. `join_requests`

신청 + 초대 통합

| 키 | 컬럼 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `BIGINT AUTO_INCREMENT` |  |
|  | `direction` | `VARCHAR(10)` | APPLY\|INVITE |
|  | `target_team_id` | `BIGINT` | → teams · 둘 중 하나만 값을 가짐(exclusive-arc), 검증은 애플리케이션에서 |
|  | `target_group_id` | `BIGINT` | → groups_ · 둘 중 하나만 값을 가짐(exclusive-arc), 검증은 애플리케이션에서 |
|  | `user_id` | `BIGINT NOT NULL` | → users · 가입 대상자 (APPLY=신청자, INVITE=초대받은 사람) — 항상 존재 |
|  | `inviter_id` | `BIGINT` | → users · 초대한 사람, `direction='INVITE'`일 때만 |
|  | `recruit_post_id` | `BIGINT` | → recruit_posts |
|  | `instrument` | `VARCHAR(20)` | 지원 세션 · GROUP 신청은 NULL |
|  | `message` | `TEXT` |  |
|  | `status` | `VARCHAR(10)` | PENDING\|APPROVED\|REJECTED\|CANCELED |
|  | `decided_by` | `BIGINT` | → users |
|  | `created_at` | `DATETIME(6)` |  |
|  | `decided_at` | `DATETIME(6)` |  |

- **인덱스** `(target_team_id, status)`, `(target_group_id, status)`, `(user_id, status)`

> 2026-09-16 변경: `target_type`+`target_id` → exclusive-arc(대상 타입별 컬럼 분리), `actor_id`/`target_user_id` → `user_id`/`inviter_id`. FK·CHECK 제약은 걸지 않는다 — 참조 컬럼은 전부 `Long` 매핑, DB 제약 없음(CLAUDE.md 절대 규칙 6, PR #29). 사유: `docs/plans/schema-cleanup-adoption-review.md` §①·§③ — `actor_id`가 `direction`에 따라 가리키는 대상이 바뀌는 문제였고, "내 신청·초대함"이 `user_id` 하나로 조회되게 하려는 목적.


---

## 공연 팀

### 12. `teams`

공연 팀 + 독립 팀

| 키 | 컬럼 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `BIGINT AUTO_INCREMENT` |  |
| FK? | `performance_id` | `BIGINT` | → performances (독립 팀) |
|  | `name` | `VARCHAR(50) NOT NULL` |  |
|  | `description` | `TEXT` |  |
|  | `region` | `VARCHAR(50)` |  |
|  | `status` | `VARCHAR(10)` | ACTIVE\|DISBANDED |
|  | `created_at` | `DATETIME(6)` |  |
| ＋ | `updated_at` | `DATETIME(6)` |  |

> genres 컬럼 → team_genres 로 분리

### 13. `team_genres`

장르 (신규)

| 키 | 컬럼 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `BIGINT AUTO_INCREMENT` |  |
| FK | `team_id` | `BIGINT` | → teams |
|  | `genre` | `VARCHAR(30)` |  |

- **유니크** `(team_id, genre)`
- **인덱스** `(genre)  ← GET /teams?genre= 필터용`

### 14. `team_members`

팀원 + 세션

| 키 | 컬럼 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `BIGINT AUTO_INCREMENT` |  |
| FK | `team_id` | `BIGINT` | → teams |
| FK | `user_id` | `BIGINT` | → users |
|  | `role` | `VARCHAR(10)` | LEADER\|MEMBER · 위임 가능 |
|  | `instrument` | `VARCHAR(20) NOT NULL` |  |
|  | `status` | `VARCHAR(10)` | ACTIVE\|LEFT\|REMOVED · 7.1.2 |
|  | `joined_at` | `DATETIME(6)` |  |

- **유니크** `(team_id, user_id)`

### 15. `team_songs`

팀 선택곡 — 독립 팀·공연 팀이 고른 곡. 팀 쪽에서는 "셋리스트"라고 부르지 않는다

| 키 | 컬럼 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `BIGINT AUTO_INCREMENT` |  |
| FK | `team_id` | `BIGINT NOT NULL` | → teams |
| FK? | `performance_id` | `BIGINT` | → performances. `teams.performance_id` 복사본, 독립 팀이면 NULL |
| FK | `song_id` | `BIGINT NOT NULL` | → songs |
|  | `created_at` | `DATETIME(6)` |  |
| ＋ | `updated_at` | `DATETIME(6)` |  |

- **유니크** `(team_id, song_id)` — 같은 팀이 같은 곡을 두 번 고를 수 없다
- **인덱스** `(performance_id)` ← 같은 공연의 다른 팀이 고른 곡 조회용 ("다른 팀이 이미 골랐어요" 표시)

> **2026-10-30 변경(이슈 #85, V9)**: 공연 셋리스트를 `setlists`(§18)로 분리하면서 `sort_order`·`progress` 삭제, `performance_id` 재추가, 위 유니크 추가. 이전에는 한 행이 팀 연습곡이면서 `progress = SELECTED`이면 셋리스트 항목이기도 해서 `sort_order`·`progress`가 뜻을 두 개씩 가졌다. `performance_id`는 2026-09-16에 `teams.performance_id`와 중복이라 지웠던 컬럼인데, `setlist` 도메인이 `teams`를 직접 조회할 수 없어(CLAUDE.md 절대 규칙 2) 되살렸다 — 팀은 생성된 공연에서 옮겨가지 않으므로 값이 어긋나지 않는다. 계획: `docs/plans/team-songs-setlists-redesign.md`.

### 18. `setlists`

공연 셋리스트 — 공연 팀이 팀 선택곡(`team_songs`) 중에서 넘긴 곡. 모임장이 순서를 정해 확정한다 (2026-10-30 신규, 이슈 #85, V10. 번호는 기존 절 번호를 지키려고 끝 번호를 붙였다)

| 키 | 컬럼 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `BIGINT AUTO_INCREMENT` |  |
| FK | `performance_id` | `BIGINT NOT NULL` | → performances |
| FK | `team_id` | `BIGINT NOT NULL` | → teams · 곡을 넘긴 팀 |
| FK | `song_id` | `BIGINT NOT NULL` | → songs |
|  | `sort_order` | `INT` | 공연 셋리스트 순서. 모임장이 확정할 때 1부터 빈틈없이 정한다(확정 전 NULL) |
|  | `created_at` | `DATETIME(6)` |  |
|  | `updated_at` | `DATETIME(6)` |  |

- **유니크** `(performance_id, song_id)` — 한 공연에 같은 곡을 두 번 넣을 수 없다(7.1.3). DB가 강제한다
- **인덱스** `(team_id)` ← 팀 페이지의 확정곡 조회, 팀 개별 해체 시 삭제용

> 행은 팀이 넘길 때 `team_songs`에서 복사한다 — 넘긴 뒤 팀이 선택곡을 고쳐도 모임장이 보는 셋리스트가 바뀌지 않게. 확정 여부는 `performances.setlist_confirmed_at`에 둔다. 팀이 **개별** 해체되면 그 팀 행을 하드 삭제해 유니크를 푼다(다른 팀이 그 곡을 넘길 수 있게). 그 팀의 `team_songs` 행도 같이 지운다. 공연 종료·취소(`DONE`·`CANCELED`)로 인한 일괄 해체에서는 둘 다 남긴다 — `architecture.md` §6의 예외. 확정을 해제하면(`PATCH .../setlist/unconfirm`) `sort_order`를 NULL로 되돌린다.

### 16. `rehearsals`

합주 일정 (6.1.3)

| 키 | 컬럼 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `BIGINT AUTO_INCREMENT` |  |
| FK | `team_id` | `BIGINT` | → teams |
|  | `title` | `VARCHAR(100)` |  |
|  | `starts_at` | `DATETIME(6)` |  |
|  | `ends_at` | `DATETIME(6)` |  |
|  | `place` | `VARCHAR(100)` |  |
|  | `memo` | `TEXT` |  |
| FK | `created_by` | `BIGINT` | → users |
| ＋ | `created_at` | `DATETIME(6)` |  |
| ＋ | `updated_at` | `DATETIME(6)` |  |

- **인덱스** `(team_id, starts_at)`


---

## 파일

### 17. `files`

업로드된 파일 메타데이터 (`global/file` — 도메인 아님, 1팀 담당. 1팀 프로필·4팀 커버·포스터·연습 녹음이 같이 씀)

| 키 | 컬럼 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `BIGINT AUTO_INCREMENT` |  |
|  | `file_key` | `VARCHAR(255) NOT NULL` | 스토리지 상 실제 경로(`{도메인}/{uuid}.{확장자}`) |
|  | `domain` | `VARCHAR(20) NOT NULL` | PROFILE_IMAGE\|GROUP_COVER\|PERFORMANCE_POSTER\|PRACTICE_AUDIO (`PRACTICE_AUDIO`는 2026-11-09 추가, 이슈 #154 — 값만 늘어 마이그레이션 없음) |
|  | `original_name` | `VARCHAR(255) NOT NULL` |  |
|  | `content_type` | `VARCHAR(100) NOT NULL` |  |
|  | `uploader_id` | `BIGINT NOT NULL` | → users · FK 아님, 인덱스만(CLAUDE.md 절대 규칙 6) |
|  | `created_at` | `DATETIME(6)` |  |

- **인덱스** `(uploader_id)`

> 2026-09-30 신규. `users.profile_image`·`groups_.cover_image`·`performances.poster_image`는 이 테이블을 참조하지 않고 여전히 URL 문자열을 직접 저장한다 — `files`는 발급·삭제 이력 관리용이고, 실제 참조는 URL 문자열로 한다(각 도메인이 `fileId`를 몰라도 되게). 상세: `docs/plans/file-upload-presigned-url.md`.
>
> 2026-11-09: 연습 녹음(`PRACTICE_AUDIO`)은 비공개라 예외다 — 연습 도메인이 URL이 아니라 `file_key`를 저장하고, 저장 전에 이 테이블로 업로더·도메인을 확인한다(이슈 #154).

---

## 관계

| 부모 | 자식 | 카디널리티 | 비고 |
|---|---|---|---|
| `users` | `user_instruments` | 1:N |  |
| `users` | `bookmarks` | 1:N |  |
| `users` | `notifications` | 1:N |  |
| `users` | `group_members` | 1:N |  |
| `users` | `team_members` | 1:N |  |
| `users` | `join_requests` | 1:N | `user_id`(가입 대상자) |
| `users` | `join_requests` | 1:N | `inviter_id`(초대한 사람, INVITE만) |
| `groups_` | `group_members` | 1:N |  |
| `groups_` | `group_notices` | 1:N |  |
| `groups_` | `performances` | 1:N | 개최 |
| `groups_` | `recruit_posts` | 1:N | `target_group_id`, exclusive-arc |
| `groups_` | `join_requests` | 1:N | `target_group_id`, exclusive-arc |
| `teams` | `recruit_posts` | 1:N | `target_team_id`, exclusive-arc |
| `teams` | `join_requests` | 1:N | `target_team_id`, exclusive-arc |
| `teams` | `bookmarks` | 1:N | `target_team_id`, exclusive-arc |
| `users` | `bookmarks` | 1:N | `target_user_id`, exclusive-arc (북마크한 사람 `user_id`와 별개) |
| `songs` | `bookmarks` | 1:N | `target_song_id`, exclusive-arc |
| `recruit_posts` | `join_requests` | 1:N |  |
| `performances` | `teams` | 0..1:N |  |
| `teams` | `team_members` | 1:N |  |
| `teams` | `team_genres` | 1:N |  |
| `teams` | `rehearsals` | 1:N |  |
| `teams` | `team_songs` | 1:N | 팀 선택곡 |
| `songs` | `team_songs` | 1:N |  |
| `performances` | `team_songs` | 0..1:N | `teams.performance_id` 복사본, 독립 팀이면 NULL |
| `performances` | `setlists` | 1:N | 공연 셋리스트 |
| `teams` | `setlists` | 1:N | 곡을 넘긴 팀 |
| `songs` | `setlists` | 1:N |  |

`created_by` · `author_id` · `decided_by` · `user_id`(join_requests) · `inviter_id` 는 모두 `users` 를 참조하지만 선이 얽혀 도면에서는 컬럼 주석으로만 표기했다.

**2026-09-16부터 `target_type`+`target_id`(다형성) 패턴은 `notifications` 한 곳만 남았다.** `recruit_posts` · `join_requests` · `bookmarks`는 exclusive-arc(대상 타입별 nullable 컬럼 분리)로 전환했다. **DB FK·CHECK 제약은 걸지 않는다** — CLAUDE.md 절대 규칙 6(다른 테이블 참조 컬럼은 `Long` 매핑, FK 없음, PR #29)이 이 프로젝트 전체에 적용되므로, exclusive-arc도 예외가 아니다. "정확히 하나만 값을 가짐" 검증은 애플리케이션에서 한다. `notifications.target_type`/`target_id`는 값 목록(`NotificationTargetType`)이 아직 확정되지 않아 전환을 보류했다 — 대상 존재 검증은 지금도 애플리케이션에서 한다. 근거: `docs/plans/schema-cleanup-adoption-review.md` §③.

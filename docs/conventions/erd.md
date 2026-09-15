# moyeorock DB 테이블 명세 — ERD v3
대상: MySQL 8.0 · 16 테이블 · PK `BIGINT AUTO_INCREMENT`
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
|  | `platform_role` | `VARCHAR(10)` | USER\|SUPER · 2.2 |
|  | `status` | `VARCHAR(20)` | ACTIVE\|WITHDRAWN\|SUSPENDED |
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
|  | `instrument` | `VARCHAR(20) NOT NULL` | VOCAL\|EL_GT\|AC_GT\|BASS\|DRUM\|KEY\|ETC |
| ＋ | `custom_instrument` | `VARCHAR(30)` | 3.2 직접 입력 · instrument가 ETC일 때만 입력 |
|  | `level` | `VARCHAR(20)` | 3.3 BEGINNER\|NOVICE\|INTERMEDIATE\|ADVANCED |

- **유니크** `(user_id, instrument, custom_instrument)` — `instrument=ETC`인 사용자가 `custom_instrument`만 다른 세션을 여러 개 등록할 수 있게 확장(2026-09-16, 기존 `(user_id, instrument)`는 ETC 중복 등록을 막아버리는 버그였음)


---

## 마이페이지

### 3. `bookmarks`

저장 목록

| 키 | 컬럼 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `BIGINT AUTO_INCREMENT` |  |
| FK | `user_id` | `BIGINT` | → users · 북마크한 사람 |
| FK? | `target_team_id` | `BIGINT` | → teams · exclusive-arc, 셋 중 하나만 |
| FK? | `target_user_id` | `BIGINT` | → users · 대상 사람 · exclusive-arc, 셋 중 하나만 |
| FK? | `target_song_id` | `BIGINT` | → songs · exclusive-arc, 셋 중 하나만 |
|  | `created_at` | `DATETIME(6)` |  |

- **체크** `CHK_BM_TARGET`: `(target_team_id IS NOT NULL) + (target_user_id IS NOT NULL) + (target_song_id IS NOT NULL) = 1`
- **유니크** `(user_id, target_team_id)` `(user_id, target_user_id)` `(user_id, target_song_id)` — MySQL 유니크 인덱스는 NULL끼리 서로 다르게 취급하므로 대상 타입별로 정확히 중복만 막는다

> 기존 `target_type`+`target_id`(다형성, FK 없음) → exclusive-arc로 전환(2026-09-16). 근거: `docs/plans/schema-cleanup-adoption-review.md` §③


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
|  | `created_at` | `DATETIME(6)` |  |
| ＋ | `updated_at` | `DATETIME(6)` |  |

### 5. `group_members`

모임원 + 권한

| 키 | 컬럼 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `BIGINT AUTO_INCREMENT` |  |
| FK | `group_id` | `BIGINT` | → groups_ |
| FK | `user_id` | `BIGINT` | → users |
|  | `role` | `VARCHAR(10)` | OWNER\|MEMBER |
|  | `status` | `VARCHAR(10)` | ACTIVE\|LEFT\|BANNED |
|  | `joined_at` | `DATETIME(6)` |  |

- **유니크** `(group_id, user_id)`

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


---

## 공용

### 7. `notifications`

알림

| 키 | 컬럼 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `BIGINT AUTO_INCREMENT` |  |
| FK | `user_id` | `BIGINT` | → users |
|  | `type` | `VARCHAR(30)` | TEAM_APPLY\|TEAM_INVITE\|... |
|  | `message` | `VARCHAR(255)` |  |
| ＋ | `target_type` | `VARCHAR(20)` | 이동 대상 |
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
|  | `venue` | `VARCHAR(100)` | 7.1.1 필수 X |
|  | `poster_image` | `VARCHAR(500)` | 7.4 |
|  | `status` | `VARCHAR(20)` | PLANNED\|RECRUITING\|DONE\|CANCELED |
| FK | `created_by` | `BIGINT` | → users |
|  | `created_at` | `DATETIME(6)` |  |
| ＋ | `updated_at` | `DATETIME(6)` |  |


---

## 모집 공고

### 10. `recruit_posts`

팀/모임 모집 (5.1, 5.2)

| 키 | 컬럼 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `BIGINT AUTO_INCREMENT` |  |
| FK? | `target_team_id` | `BIGINT` | → teams · exclusive-arc, 둘 중 하나만 |
| FK? | `target_group_id` | `BIGINT` | → groups_ · exclusive-arc, 둘 중 하나만 |
| FK | `author_id` | `BIGINT` | → users |
|  | `title` | `VARCHAR(100)` |  |
|  | `body` | `TEXT` |  |
|  | `wanted_slots` | `JSON` | 세션별 인원 |
|  | `region` | `VARCHAR(50)` | 팀/모임 region과 무관 · 작성자가 작성·수정 시 직접 입력하는 독립 값 (동기화 불필요, `docs/plans/recruit-post-region-sync.md`) |
|  | `status` | `VARCHAR(10)` | OPEN\|CLOSED |
|  | `created_at` | `DATETIME(6)` |  |
| ＋ | `updated_at` | `DATETIME(6)` |  |

- **체크** `CHK_RP_TARGET`: `(target_team_id IS NOT NULL) + (target_group_id IS NOT NULL) = 1`
- **인덱스** `(status, region, created_at)`

> 기존 `target_type`+`target_id`(다형성, FK 없음) → exclusive-arc로 전환(2026-09-16). 근거: `docs/plans/schema-cleanup-adoption-review.md` §③

### 11. `join_requests`

신청 + 초대 통합

| 키 | 컬럼 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `BIGINT AUTO_INCREMENT` |  |
|  | `direction` | `VARCHAR(10)` | APPLY\|INVITE |
| FK? | `target_team_id` | `BIGINT` | → teams · exclusive-arc, 둘 중 하나만 |
| FK? | `target_group_id` | `BIGINT` | → groups_ · exclusive-arc, 둘 중 하나만 |
| FK | `user_id` | `BIGINT NOT NULL` | → users · 가입 대상자 (APPLY=신청자, INVITE=초대받은 사람) — 항상 존재 |
| FK? | `inviter_id` | `BIGINT` | → users · 초대한 사람, `direction='INVITE'`일 때만 |
| FK? | `recruit_post_id` | `BIGINT` | → recruit_posts |
|  | `instrument` | `VARCHAR(20)` | 지원 세션 · GROUP 신청은 NULL |
|  | `message` | `TEXT` |  |
|  | `status` | `VARCHAR(10)` | PENDING\|APPROVED\|REJECTED\|CANCELED |
| FK? | `decided_by` | `BIGINT` | → users |
|  | `created_at` | `DATETIME(6)` |  |
|  | `decided_at` | `DATETIME(6)` |  |

- **체크** `CHK_JR_TARGET`: `(target_team_id IS NOT NULL) + (target_group_id IS NOT NULL) = 1`
- **인덱스** `(target_team_id, status)` `(target_group_id, status)` `(user_id, status)`

> 2026-09-16 변경: `target_type`+`target_id` → exclusive-arc, `actor_id`/`target_user_id` → `user_id`/`inviter_id`. 사유: `docs/plans/schema-cleanup-adoption-review.md` §①·§③ — `actor_id`가 `direction`에 따라 가리키는 대상이 바뀌는 문제였고, "내 신청·초대함"이 `user_id` 하나로 조회되게 하려는 목적.


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

연습곡 + 셋리스트

| 키 | 컬럼 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `BIGINT AUTO_INCREMENT` |  |
| FK | `team_id` | `BIGINT` | → teams |
| FK | `song_id` | `BIGINT` | → songs |
|  | `sort_order` | `INT` | 7.1.3 순서 |
|  | `progress` | `VARCHAR(20)` | CANDIDATE\|SELECTED\|PRACTICING\|DONE |
|  | `created_at` | `DATETIME(6)` |  |
| ＋ | `updated_at` | `DATETIME(6)` |  |

> 2026-09-16 변경: `performance_id`(→ performances, `teams.performance_id`와 중복)와 `selected_song_id`(GENERATED) + `UNIQUE(performance_id, selected_song_id)` 삭제. 어느 공연 소속인지는 `team_id → teams.performance_id`로 파생한다(팀은 생성된 공연에서 옮겨가지 않으므로 값이 어긋날 일이 없다). **대신 "공연 내 곡 중복 방지"(7.1.3)를 DB가 더 이상 강제하지 않는다** — `setlist` 도메인(4팀) `SetlistService`가 `progress='SELECTED'`로 바꾸는 시점에 같은 공연 소속 팀들의 `team_songs`를 조회해 중복을 애플리케이션에서 검증해야 한다. 근거: `docs/plans/schema-cleanup-adoption-review.md` "`team_songs.performance_id` — 삭제 확정" 절.

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
| `teams` | `team_songs` | 1:N |  |
| `songs` | `team_songs` | 1:N |  |

`created_by` · `author_id` · `decided_by` · `user_id`(join_requests) · `inviter_id` 는 모두 `users` 를 참조하지만 선이 얽혀 도면에서는 컬럼 주석으로만 표기했다.

**2026-09-16부터 `target_type`+`target_id`(다형성, FK 없음) 패턴은 `notifications` 한 곳만 남았다.** `recruit_posts` · `join_requests` · `bookmarks`는 exclusive-arc(대상 타입별 nullable FK + `CHECK` 제약)로 전환해 실제 FK 무결성이 생겼다. `notifications.target_type`/`target_id`는 값 목록(`NotificationTargetType`)이 아직 확정되지 않아 전환을 보류했다 — 대상 존재 검증은 지금도 애플리케이션에서 한다. 근거: `docs/plans/schema-cleanup-adoption-review.md` §③.

# moyeorock DB 테이블 명세 — ERD v3
대상: MySQL 8.0 · 16 테이블 · PK `BIGINT AUTO_INCREMENT`
기준: `band_erd_v3_mysql.drawio` 에서 자동 생성


---

## 회원 관리

### 1. `users`

사용자

| 키 | 컬럼 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `BIGINT AUTO_INCREMENT` |  |
| UK | `email` | `VARCHAR(255) NULL` | 유일 · 소셜 전용은 NULL |
|  | `password_hash` | `VARCHAR(255) NULL` |  |
| UK | `kakao_id` | `VARCHAR(64) NULL` | 유일 · 1.2 |
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
| ＋ | `onboarding_completed_at` | `DATETIME(6) NULL` |  |
|  | `withdrawn_at` | `DATETIME(6) NULL` | 1.1 |
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
|  | `instrument` | `VARCHAR(20)` | VOCAL\|EL_GT\|AC_GT\|BASS\|DRUM\|KEY\|ETC |
| ＋ | `custom_instrument` | `VARCHAR(30) NULL` | 3.2 직접 입력 |
|  | `level` | `VARCHAR(20)` | 3.3 BEGINNER\|NOVICE\|INTERMEDIATE\|ADVANCED |

- **유니크** `(user_id, instrument)`


---

## 마이페이지

### 3. `bookmarks`

저장 목록

| 키 | 컬럼 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `BIGINT AUTO_INCREMENT` |  |
| FK | `user_id` | `BIGINT` | → users |
|  | `target_type` | `VARCHAR(10)` | TEAM\|USER\|SONG |
|  | `target_id` | `BIGINT` | 다형성 · FK 없음 |
|  | `created_at` | `DATETIME(6)` |  |

- **유니크** `(user_id, target_type, target_id)`


---

## 동아리

### 4. `groups`

모임

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
| FK | `group_id` | `BIGINT` | → groups |
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
| FK | `group_id` | `BIGINT` | → groups |
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
| ＋ | `target_type` | `VARCHAR(20) NULL` | 이동 대상 |
| ＋ | `target_id` | `BIGINT NULL` |  |
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
| FK | `group_id` | `BIGINT` | → groups NOT NULL |
|  | `title` | `VARCHAR(100)` |  |
|  | `description` | `TEXT` |  |
|  | `performed_at` | `DATETIME(6) NULL` |  |
|  | `venue` | `VARCHAR(100) NULL` | 7.1.1 필수 X |
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
|  | `target_type` | `VARCHAR(10)` | TEAM\|GROUP |
|  | `target_id` | `BIGINT` | 다형성 · FK 없음 |
| FK | `author_id` | `BIGINT` | → users |
|  | `title` | `VARCHAR(100)` |  |
|  | `body` | `TEXT` |  |
|  | `wanted_slots` | `JSON` | 세션별 인원 |
|  | `region` | `VARCHAR(50)` |  |
|  | `status` | `VARCHAR(10)` | OPEN\|CLOSED |
|  | `created_at` | `DATETIME(6)` |  |
| ＋ | `updated_at` | `DATETIME(6)` |  |

- **인덱스** `(status, region, created_at)`

### 11. `join_requests`

신청 + 초대 통합

| 키 | 컬럼 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `BIGINT AUTO_INCREMENT` |  |
|  | `direction` | `VARCHAR(10)` | APPLY\|INVITE |
|  | `target_type` | `VARCHAR(10)` | TEAM\|GROUP |
|  | `target_id` | `BIGINT` | 다형성 · FK 없음 |
| FK | `actor_id` | `BIGINT` | → users · 신청/초대한 사람 |
| FK? | `target_user_id` | `BIGINT` | → users NULL · 9.4 |
| FK? | `recruit_post_id` | `BIGINT` | → recruit_posts NULL |
|  | `instrument` | `VARCHAR(20) NULL` | 지원 세션 |
|  | `message` | `TEXT` |  |
|  | `status` | `VARCHAR(10)` | PENDING\|APPROVED\|REJECTED\|CANCELED |
| FK? | `decided_by` | `BIGINT` | → users NULL |
|  | `created_at` | `DATETIME(6)` |  |
|  | `decided_at` | `DATETIME(6) NULL` |  |


---

## 공연 팀

### 12. `teams`

공연 팀 + 독립 팀

| 키 | 컬럼 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `BIGINT AUTO_INCREMENT` |  |
| FK? | `performance_id` | `BIGINT` | → performances NULL (NULL = 독립 팀) |
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
|  | `instrument` | `VARCHAR(20)` |  |
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
| FK? | `performance_id` | `BIGINT` | → performances NULL |
|  | `sort_order` | `INT` | 7.1.3 순서 |
|  | `progress` | `VARCHAR(20)` | CANDIDATE\|SELECTED\|PRACTICING\|DONE |
|  | `created_at` | `DATETIME(6)` |  |
| ＋ | `updated_at` | `DATETIME(6)` |  |
| ＋ | `selected_song_id` | `BIGINT GENERATED STORED` | = IF(progress='SELECTED', song_id, NULL) → 공연 내 곡 중복 방지 (7.1.3) |

- **유니크** `(performance_id, selected_song_id)`

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
| `users` | `join_requests` | 1:N | actor |
| `groups` | `group_members` | 1:N |  |
| `groups` | `group_notices` | 1:N |  |
| `groups` | `performances` | 1:N | 개최 |
| `recruit_posts` | `join_requests` | 1:N |  |
| `performances` | `teams` | 0..1:N |  |
| `teams` | `team_members` | 1:N |  |
| `teams` | `team_genres` | 1:N |  |
| `teams` | `rehearsals` | 1:N |  |
| `teams` | `team_songs` | 1:N |  |
| `songs` | `team_songs` | 1:N |  |

`created_by` · `author_id` · `decided_by` · `target_user_id` 는 모두 `users` 를 참조하지만 선이 얽혀 도면에서는 컬럼 주석으로만 표기했다.

`target_id` (다형성) 는 `recruit_posts` · `join_requests` · `bookmarks` · `notifications` 4곳에 있고 **FK 제약이 없다.** 대상 존재 검증은 애플리케이션에서 한다.

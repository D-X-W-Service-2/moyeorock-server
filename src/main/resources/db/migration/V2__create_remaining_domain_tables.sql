-- 3·1·4팀이 각자 나눠서 마이그레이션을 만들면 버전 번호가 충돌하므로, erd.md의 남은 13개
-- 테이블(users ~ rehearsals)을 이번 PR에서 한 번에 만든다. team/team_genres/team_members는
-- V1에 이미 있어 여기서 다시 만들지 않는다. FK 제약은 걸지 않는다(CLAUDE.md 절대 규칙 6).

-- 1. users -------------------------------------------------------------
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255),
    password_hash VARCHAR(255),
    kakao_id VARCHAR(64),
    nickname VARCHAR(20) NOT NULL,
    region VARCHAR(50),
    genres JSON,
    bio TEXT,
    profile_image VARCHAR(500),
    platform_role VARCHAR(10) NOT NULL,
    status VARCHAR(20) NOT NULL,
    is_recommendable TINYINT(1) NOT NULL DEFAULT 1,
    is_activity_public TINYINT(1) NOT NULL DEFAULT 1,
    privacy_agreed_at DATETIME(6) NOT NULL,
    onboarding_completed_at DATETIME(6),
    withdrawn_at DATETIME(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);

CREATE UNIQUE INDEX uk_users_email ON users (email);
CREATE UNIQUE INDEX uk_users_kakao_id ON users (kakao_id);
CREATE UNIQUE INDEX uk_users_nickname ON users (nickname);
-- 사용자 검색 API(ACTIVE 필터 + 닉네임 정렬)에서 쓴다.
CREATE INDEX idx_users_status_nickname ON users (status, nickname);

-- 2. user_instruments ----------------------------------------------------
CREATE TABLE user_instruments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,   -- FK 아님, 인덱스만
    instrument VARCHAR(20) NOT NULL,
    level VARCHAR(20) NOT NULL
);

CREATE UNIQUE INDEX uk_user_instruments_user_id_instrument ON user_instruments (user_id, instrument);

-- 3. bookmarks -------------------------------------------------------------
CREATE TABLE bookmarks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,            -- FK 아님, 인덱스만
    target_team_id BIGINT,     -- exclusive-arc, 셋 중 하나만
    target_user_id BIGINT,
    target_song_id BIGINT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

-- MySQL 유니크 인덱스는 NULL끼리 서로 다르게 취급하므로 대상 타입별로 정확히 중복만 막는다.
CREATE UNIQUE INDEX uk_bookmarks_user_id_target_team_id ON bookmarks (user_id, target_team_id);
CREATE UNIQUE INDEX uk_bookmarks_user_id_target_user_id ON bookmarks (user_id, target_user_id);
CREATE UNIQUE INDEX uk_bookmarks_user_id_target_song_id ON bookmarks (user_id, target_song_id);

-- 4. groups_ (예약어 회피, erd.md §4) --------------------------------------
CREATE TABLE groups_ (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    description TEXT,
    type VARCHAR(10),
    region VARCHAR(50),
    cover_image VARCHAR(500),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);

-- 5. group_members ----------------------------------------------------------
CREATE TABLE group_members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id BIGINT NOT NULL,  -- FK 아님, 인덱스만
    user_id BIGINT NOT NULL,   -- FK 아님, 인덱스만
    role VARCHAR(10) NOT NULL,
    status VARCHAR(10) NOT NULL,
    joined_at DATETIME(6) NOT NULL
);

CREATE UNIQUE INDEX uk_group_members_group_id_user_id ON group_members (group_id, user_id);

-- 6. group_notices ---------------------------------------------------------
CREATE TABLE group_notices (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id BIGINT NOT NULL,  -- FK 아님, 인덱스만
    author_id BIGINT NOT NULL, -- FK 아님, 인덱스만
    title VARCHAR(100) NOT NULL,
    body TEXT NOT NULL,
    is_pinned TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);

-- 7. notifications -----------------------------------------------------------
-- type 값 목록은 erd.md의 "TEAM_APPLY|TEAM_INVITE|..."(전체 목록 미확정)를 팀 확인 받아
-- domain/notification/enums/NotificationType으로 채웠다(2026-09-29) — VARCHAR(30)은 그대로 두되
-- 저장되는 값은 이제 @Enumerated(STRING)이다.
CREATE TABLE notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,             -- FK 아님, 인덱스만
    type VARCHAR(30),
    message VARCHAR(255),
    target_type VARCHAR(20),
    target_id BIGINT,
    is_read TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE INDEX idx_notifications_user_id_is_read_created_at ON notifications (user_id, is_read, created_at);

-- 8. songs --------------------------------------------------------------
CREATE TABLE songs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200),
    artist VARCHAR(100),
    genre VARCHAR(30),
    song_key VARCHAR(10),
    bpm INT,
    difficulty JSON,
    external_id VARCHAR(100),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE UNIQUE INDEX uk_songs_external_id ON songs (external_id);

-- 9. performances -----------------------------------------------------
CREATE TABLE performances (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id BIGINT NOT NULL,  -- FK 아님, 인덱스만
    title VARCHAR(100),
    description TEXT,
    performed_at DATETIME(6),
    venue VARCHAR(100),        -- 7.1.1 필수 X
    poster_image VARCHAR(500),
    status VARCHAR(20),
    created_by BIGINT,         -- FK 아님, 인덱스만
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);

-- 10. recruit_posts (exclusive-arc, erd.md 2026-09-16 전환) ------------------
CREATE TABLE recruit_posts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    target_team_id BIGINT,     -- exclusive-arc, 둘 중 하나만
    target_group_id BIGINT,
    author_id BIGINT NOT NULL, -- FK 아님, 인덱스만
    title VARCHAR(100) NOT NULL,
    body TEXT NOT NULL,
    wanted_slots JSON NOT NULL,
    region VARCHAR(50),
    status VARCHAR(10) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);

CREATE INDEX idx_recruit_posts_status_region_created_at ON recruit_posts (status, region, created_at);
CREATE INDEX idx_recruit_posts_target_team_id ON recruit_posts (target_team_id);
CREATE INDEX idx_recruit_posts_target_group_id ON recruit_posts (target_group_id);

-- 11. join_requests (exclusive-arc + user_id/inviter_id 개명, erd.md 2026-09-16) ---
CREATE TABLE join_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    direction VARCHAR(10) NOT NULL,
    target_team_id BIGINT,     -- exclusive-arc, 둘 중 하나만
    target_group_id BIGINT,
    user_id BIGINT NOT NULL,   -- 가입 대상자(APPLY=신청자, INVITE=초대받은 사람), 항상 존재
    inviter_id BIGINT,         -- 초대한 사람, direction='INVITE'일 때만
    recruit_post_id BIGINT,
    instrument VARCHAR(20),    -- GROUP 신청은 NULL
    message TEXT,
    status VARCHAR(10) NOT NULL,
    decided_by BIGINT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    decided_at DATETIME(6)
);

CREATE INDEX idx_join_requests_target_team_id_status ON join_requests (target_team_id, status);
CREATE INDEX idx_join_requests_target_group_id_status ON join_requests (target_group_id, status);
CREATE INDEX idx_join_requests_user_id_status ON join_requests (user_id, status);

-- 15. team_songs (performance_id·selected_song_id 삭제, erd.md 2026-09-16) -----
-- 공연 내 곡 중복 방지는 이제 DB가 강제하지 않는다 — setlist 도메인(4팀) Service가 애플리케이션
-- 레벨에서 검증해야 한다(team_songs.getStatus() 등 후속 구현 필요, TeamSong.java 상단 주석 참고).
CREATE TABLE team_songs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id BIGINT NOT NULL,   -- FK 아님, 인덱스만
    song_id BIGINT NOT NULL,   -- FK 아님, 인덱스만
    sort_order INT,
    progress VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);

CREATE INDEX idx_team_songs_team_id ON team_songs (team_id);

-- 16. rehearsals ----------------------------------------------------------
CREATE TABLE rehearsals (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id BIGINT NOT NULL,   -- FK 아님, 인덱스만
    title VARCHAR(100),
    starts_at DATETIME(6),
    ends_at DATETIME(6),
    place VARCHAR(100),
    memo TEXT,
    created_by BIGINT,         -- FK 아님, 인덱스만
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);

CREATE INDEX idx_rehearsals_team_id_starts_at ON rehearsals (team_id, starts_at);

-- users, user_instruments (1팀 user 도메인 — erd.md §1·§2)
-- FK 제약 걸지 않음 — docs/conventions/flyway-migration.md §3-2

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NULL,                 -- 소셜 전용 계정은 NULL. 탈퇴 시 NULL로 비움
    password_hash VARCHAR(255) NULL,
    kakao_id VARCHAR(64) NULL,               -- 탈퇴 시 NULL로 비움
    nickname VARCHAR(20) NOT NULL,
    region VARCHAR(50) NULL,
    genres JSON NULL,                        -- 선호 장르 배열, 예: ["ROCK", "INDIE"]
    bio TEXT NULL,
    profile_image VARCHAR(500) NULL,
    platform_role VARCHAR(10) NOT NULL,      -- USER | SUPER
    status VARCHAR(20) NOT NULL,             -- ACTIVE | WITHDRAWN | SUSPENDED
    is_recommendable TINYINT(1) NOT NULL DEFAULT 1,
    is_activity_public TINYINT(1) NOT NULL DEFAULT 1,
    privacy_agreed_at DATETIME(6) NOT NULL,
    onboarding_completed_at DATETIME(6) NULL,
    withdrawn_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);

CREATE UNIQUE INDEX uk_users_email ON users (email);
CREATE UNIQUE INDEX uk_users_kakao_id ON users (kakao_id);
CREATE UNIQUE INDEX uk_users_nickname ON users (nickname);
CREATE INDEX idx_users_status_nickname ON users (status, nickname);   -- 사용자 검색 (ACTIVE + 닉네임 정렬)

-- custom_instrument 컬럼은 두지 않는다: 악기 ETC 제거 결정(2026-09-28)으로 직접 입력 세션이 없어짐 (erd.md §2 같은 PR에서 갱신).
CREATE TABLE user_instruments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,                 -- FK 아님, 인덱스만
    instrument VARCHAR(20) NOT NULL,         -- VOCAL | EL_GT | AC_GT | BASS | DRUM | KEY
    level VARCHAR(20) NOT NULL               -- BEGINNER | NOVICE | INTERMEDIATE | ADVANCED
);

CREATE UNIQUE INDEX uk_user_instruments_user_id_instrument ON user_instruments (user_id, instrument);

-- team_members.user_id는 여기서 추가하지 않는다. 2팀이 TeamMember 엔티티에 Long userId를 살릴 때
-- 자기 마이그레이션에서 컬럼 + UNIQUE(team_id, user_id)를 함께 추가한다 (이슈 #27 D-1).

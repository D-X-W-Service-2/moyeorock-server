-- teams, team_genres, team_members (issue #15로 이미 구현된 엔티티: Team, TeamGenre, TeamMember)
-- FK 제약 걸지 않음 — docs/conventions/flyway-migration.md §3-2

CREATE TABLE teams (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    description TEXT,
    region VARCHAR(50),
    status VARCHAR(10) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);

-- performance_id는 아직 없음: Team.java의 @ManyToOne Performance 필드가 4팀 Performance 엔티티
-- 부재로 주석 처리돼 있음. Performance 작업 시 V2에서 performance_id BIGINT NULL 추가.

CREATE TABLE team_genres (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id BIGINT NOT NULL,   -- FK 아님, 인덱스만
    genre VARCHAR(30) NOT NULL
);

CREATE UNIQUE INDEX uk_team_genres_team_id_genre ON team_genres (team_id, genre);
CREATE INDEX idx_team_genres_genre ON team_genres (genre);

CREATE TABLE team_members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id BIGINT NOT NULL,   -- FK 아님, 인덱스만
    role VARCHAR(10) NOT NULL,
    instrument VARCHAR(20) NOT NULL,
    status VARCHAR(10) NOT NULL,
    joined_at DATETIME(6) NOT NULL
);

CREATE INDEX idx_team_members_team_id ON team_members (team_id);

-- user_id는 아직 없음: TeamMember.java의 @ManyToOne User 필드가 1팀 User 엔티티 부재로
-- 주석 처리돼 있음(엔티티 코드 자체 주석 참고). User 작업 시 V2에서
-- user_id BIGINT NOT NULL 추가 + UNIQUE(team_id, user_id)를 함께 반영한다
-- (erd.md "유니크 (team_id, user_id)").

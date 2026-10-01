-- V1이 예고했던 두 컬럼을 채운다 — User·Performance 엔티티가 없어서 비워뒀던 부분이
-- 이번 PR에서 두 엔티티가 생기며 풀렸다(V1__create_team_tables.sql 주석 참고).
--
-- team_members에 NOT NULL로 user_id를 추가하는데도 DEFAULT를 안 준다 — 지킬 데이터가 없는
-- 껍데기 테이블이기 때문이다(PR #50 코멘트: 공유 dev DB 자체가 없고 각자 로컬 볼륨뿐).
-- 로컬에 team_members 행이 이미 있는 사람은 이 마이그레이션 전에 `docker compose down -v`로
-- 초기화해야 한다.

ALTER TABLE teams
    ADD COLUMN performance_id BIGINT;  -- FK 아님, 인덱스만. NULL = 독립 팀(erd.md §12)

ALTER TABLE team_members
    ADD COLUMN user_id BIGINT NOT NULL;  -- FK 아님, 인덱스만

CREATE UNIQUE INDEX uk_team_members_team_id_user_id ON team_members (team_id, user_id);

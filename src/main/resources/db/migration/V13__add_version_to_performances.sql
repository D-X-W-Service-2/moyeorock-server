-- 공연 낙관적 락 버전(이슈 #103). 셋리스트 넘기기가 확정·해제와 정확히 겹치면, 넘기기는 공연을 읽을 때의 버전을
-- 커밋 때 다시 확인해서 그사이 확정·해제가 먼저 커밋됐으면 실패한다(409). 공연 행을 고치는 모든 쓰기가 버전을 올린다.
-- 기존 행은 0으로 채운다(flyway-migration.md §3-3 — NOT NULL 컬럼은 DEFAULT를 둔다).

ALTER TABLE performances
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

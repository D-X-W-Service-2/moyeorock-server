-- team_songs를 "팀 선택곡"(모든 팀이 고른 곡)으로만 쓴다(이슈 #85). 공연 셋리스트는 V10의 setlists로 옮긴다.
-- 지금까지는 한 행이 팀 연습곡이면서 progress = SELECTED이면 셋리스트 항목이기도 해서, sort_order(팀 안 순서 /
-- 공연 순서)와 progress(연습 진행도 / 모임장 확정)가 뜻을 두 개씩 가졌다.
--
-- progress·sort_order — 진행도는 새 흐름에 없고, 순서는 셋리스트(setlists.sort_order)에서만 정한다.
-- performance_id — teams.performance_id를 복사해 둔다. 2026-09-16에 "중복"이라 지운 컬럼을 되살리는 것이다.
--   setlist 도메인은 teams를 직접 조회할 수 없어서(CLAUDE.md 절대 규칙 2) "같은 공연의 다른 팀이 고른 곡"을
--   찾으려면 이 컬럼이 필요하다. 팀은 생성된 공연에서 옮겨가지 않으므로 값이 어긋나지 않는다. 독립 팀이면 NULL.
--
-- team_songs에 쓰는 API가 아직 없어 데이터 이전은 없다. 유니크 인덱스는 중복 행이 있으면 실패한다.
-- 버전 번호: #67 V6~V8 다음.

ALTER TABLE team_songs
    DROP COLUMN progress,
    DROP COLUMN sort_order,
    ADD COLUMN performance_id BIGINT AFTER team_id;  -- FK 아님, 인덱스만

-- 아래 유니크 인덱스의 첫 컬럼이 team_id라 단일 인덱스를 대신한다
DROP INDEX idx_team_songs_team_id ON team_songs;

-- 같은 팀이 같은 곡을 두 번 고를 수 없다
CREATE UNIQUE INDEX uk_team_songs_team_id_song_id ON team_songs (team_id, song_id);

-- 같은 공연의 다른 팀이 고른 곡 조회용
CREATE INDEX idx_team_songs_performance_id ON team_songs (performance_id);

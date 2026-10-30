-- 공연 셋리스트(이슈 #85). 공연 팀이 팀 선택곡(team_songs) 중에서 넘긴 곡이 한 행이고, 모임장이 순서를 정해
-- 확정한다. 넘길 때 team_songs에서 복사한다 — 넘긴 뒤 팀이 선택곡을 고쳐도 모임장이 보는 셋리스트가 바뀌면 안 된다.
--
-- UNIQUE(performance_id, song_id) — 한 공연에 같은 곡을 두 번 넣을 수 없다(7.1.3). 2026-09-16에 team_songs에서
-- 지운 뒤 애플리케이션 검증으로만 남았던 것을 DB가 다시 맡는다. 팀이 개별 해체되면 그 팀 행을 지워 유니크를 푼다.

CREATE TABLE setlists (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    performance_id BIGINT NOT NULL,  -- FK 아님, 인덱스만
    team_id BIGINT NOT NULL,         -- FK 아님, 인덱스만
    song_id BIGINT NOT NULL,         -- FK 아님, 인덱스만
    sort_order INT,                  -- 공연 셋리스트 순서. 모임장이 확정할 때 정한다(확정 전 NULL)
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);

-- 첫 컬럼이 performance_id라 공연 셋리스트 조회도 이 인덱스를 탄다
CREATE UNIQUE INDEX uk_setlists_performance_id_song_id ON setlists (performance_id, song_id);

-- 팀 페이지의 확정곡 조회, 팀 개별 해체 시 삭제용
CREATE INDEX idx_setlists_team_id ON setlists (team_id);

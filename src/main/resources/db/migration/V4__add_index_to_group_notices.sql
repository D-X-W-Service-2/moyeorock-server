-- group_notices는 V2에서 group_id를 "FK 아님, 인덱스만"으로 적어두고 인덱스를 빼먹었다.
-- 같은 V2의 다른 자식 테이블(notifications·join_requests·recruit_posts)은 모두 걸려 있어 이 테이블만 누락이었다.
--
-- 공지 쿼리 세 개가 모두 "모임으로 좁히고 → 고정 여부로 필터하거나 정렬 → 최신순 → LIMIT" 모양이다.
--   WHERE group_id = ?                    ORDER BY is_pinned DESC, created_at DESC  (공지 목록)
--   WHERE group_id = ? AND is_pinned = ?   ORDER BY created_at DESC                 (고정 필터 · 홈 미리보기)
-- (group_id)만 걸면 행은 찾아도 정렬을 못 해서 그 모임의 공지를 전부 읽고 filesort한 뒤 LIMIT을 적용한다.
-- 복합으로 두면 등치 조건 뒤가 이미 created_at 순서라 인덱스가 ORDER BY를 만족하고(MySQL 8.0 역방향 스캔)
-- LIMIT에서 바로 멈춘다. erd.md의 notifications (user_id, is_read, created_at)와 같은 패턴이다.

CREATE INDEX idx_group_notices_group_id_is_pinned_created_at
    ON group_notices (group_id, is_pinned, created_at);

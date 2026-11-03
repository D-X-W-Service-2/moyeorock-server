-- performances는 V2에서 group_id를 "FK 아님, 인덱스만"으로 적어두고 인덱스를 빼먹었다(V6 group_notices와 같은 누락).
--
-- 모임 공연 목록(GET /v1/groups/{groupId}/performances, 이슈 #83)이
--   WHERE group_id = ? ORDER BY performed_at DESC, id DESC LIMIT ?
-- 모양이다. (group_id)만 걸면 그 모임의 공연을 전부 읽고 filesort한 뒤 LIMIT을 적용한다. 복합으로 두면 등치 조건
-- 뒤가 이미 performed_at 순서라(InnoDB가 끝에 PK를 붙인다) 인덱스가 ORDER BY를 만족하고 LIMIT에서 바로 멈춘다.

CREATE INDEX idx_performances_group_id_performed_at
    ON performances (group_id, performed_at);

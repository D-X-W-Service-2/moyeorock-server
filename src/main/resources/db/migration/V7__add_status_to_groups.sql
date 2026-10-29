-- 모임 해체(PATCH /v1/groups/{groupId}/status, 이슈 #80)를 위한 상태 컬럼. 해체는 행을 지우지 않는
-- soft delete라(architecture.md §6) 상태로 표현한다. 기존 행은 DEFAULT로 전부 ACTIVE가 된다.
--
-- 인덱스는 걸지 않는다 — 모임은 PK로 찾거나 group_members를 거쳐 들어온 뒤 상태를 거르므로
-- status 단독 인덱스를 탈 조회가 없다.
--
-- 버전 번호: main V4(recruit) · #61 V5(files) · #67 V6(공지 인덱스, 머지 시 V4에서 옮겨질 예정) 다음.

ALTER TABLE groups_
    ADD COLUMN status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE';

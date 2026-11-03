-- 내 모임 목록(GET /v1/groups/my, 이슈 #80)은 group_members를 user_id로 좁힌다.
-- 기존 유니크 (group_id, user_id)는 group_id가 앞이라 user_id만으로는 탈 수 없어 전체를 읽게 된다.
-- 한 사람이 속한 모임은 몇 개뿐이라 (user_id) 단독이면 충분하다 — status 필터·joined_at 정렬은
-- 그 몇 행 위에서 끝난다(InnoDB 보조 인덱스 끝에 PK가 붙어 행 조회도 바로 된다).

CREATE INDEX idx_group_members_user_id ON group_members (user_id);

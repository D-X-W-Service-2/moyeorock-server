-- 공연 셋리스트 확정 시각(이슈 #85). NULL이면 미확정이다. 확정 여부를 셋리스트 행에서 추론하지 않고
-- 공연에 따로 두는 이유: 확정된 뒤 팀이 곡을 더 넘기지 못하게 막는 기준이 분명해지고, 빈 셋리스트를 확정한
-- 경우도 표현할 수 있다. 모임장이 다시 확정하면 시각을 갱신한다.

ALTER TABLE performances
    ADD COLUMN setlist_confirmed_at DATETIME(6);

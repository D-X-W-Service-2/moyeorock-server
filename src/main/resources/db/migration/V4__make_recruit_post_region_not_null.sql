-- recruit_posts.region NOT NULL (erd.md §10, docs/plans/recruit-post-region-not-null.md)
-- 명세(Notion API 초안 공고 작성 Required)와 요청 DTO는 이미 필수였고 스키마만 nullable이었다.
-- 기존에 NULL 행이 있으면 여기서 실패해 드러난다(임의 값으로 채우지 않는다).
ALTER TABLE recruit_posts MODIFY COLUMN region VARCHAR(50) NOT NULL;

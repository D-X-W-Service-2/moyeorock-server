-- files (global/file — 도메인 아님, 1팀 담당. erd.md #17 참고). FK 없음(CLAUDE.md 절대 규칙 6).
-- 버전 번호 참고: V2·V3은 다른 진행 중인 PR(#59)이 이미 쓰고 있어서 충돌을 피하려고 V4로 잡았다.
-- 머지 순서상 겹치면 flyway-migration.md §2에 따라 머지하는 쪽에서 재조정한다.

CREATE TABLE files (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_key VARCHAR(255) NOT NULL,
    domain VARCHAR(20) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    uploader_id BIGINT NOT NULL,   -- FK 아님, 인덱스만
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE INDEX idx_files_uploader_id ON files (uploader_id);
CREATE UNIQUE INDEX uk_files_file_key ON files (file_key);

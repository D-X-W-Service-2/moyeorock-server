-- 개인 연습 녹음 AI 분석(이슈 #155, erd.md §19·§20). FK 없음(CLAUDE.md 절대 규칙 6).
-- 버전 번호 참고: 작성 시점 origin/main의 마지막이 V12라 V13으로 뒀다. 다른 브랜치(#147·#150)도 V13을 쓰고 있어
-- 그쪽이 먼저 머지되면 flyway-migration.md §2(머지하는 쪽에서 재조정)에 따라 번호를 올린다.

CREATE TABLE practice_recordings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,   -- FK 아님, 인덱스만
    title VARCHAR(100) NOT NULL,
    part VARCHAR(20) NOT NULL,   -- 녹음한 세션(공용 Instrument) — 혼자 연습한 세션 하나의 녹음이다
    audio_file_key VARCHAR(255) NOT NULL,   -- URL이 아니라 스토리지 key(녹음은 비공개)
    status VARCHAR(10) NOT NULL,
    failure_reason VARCHAR(255),
    analyzed_at DATETIME(6),
    -- 진행 중(SUBMITTED·ANALYZING)일 때만 user_id, 아니면 NULL — 한 사람당 진행 중 분석 1개를 유니크로 막는다
    -- (flyway-migration.md §3-5). 엔티티에는 매핑하지 않는다.
    active_user_id BIGINT GENERATED ALWAYS AS (IF(status IN ('SUBMITTED', 'ANALYZING'), user_id, NULL)) STORED,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);

CREATE INDEX idx_practice_recordings_user_id_created_at ON practice_recordings (user_id, created_at);
CREATE UNIQUE INDEX uk_practice_recordings_active_user_id ON practice_recordings (active_user_id);

CREATE TABLE practice_feedbacks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recording_id BIGINT NOT NULL,   -- FK 아님, 인덱스만
    start_sec INT NOT NULL,
    end_sec INT NOT NULL,
    part VARCHAR(20) NOT NULL,
    content TEXT NOT NULL
);

CREATE INDEX idx_practice_feedbacks_recording_id_start_sec ON practice_feedbacks (recording_id, start_sec);

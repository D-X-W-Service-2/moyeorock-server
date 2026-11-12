-- Recommendation outcome logging. IDs are logical references; project migrations do not add FK constraints.
CREATE TABLE recommendation_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id CHAR(36) NOT NULL,
    actor_user_id BIGINT NOT NULL,
    team_id BIGINT,
    mode VARCHAR(20) NOT NULL,
    candidate_type VARCHAR(10) NOT NULL,
    candidate_id BIGINT NOT NULL,
    event_type VARCHAR(20) NOT NULL,
    position INT,
    score DECIMAL(8, 5),
    ranker VARCHAR(100),
    occurred_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE UNIQUE INDEX uk_recommendation_event_action
    ON recommendation_events (request_id, actor_user_id, candidate_type, candidate_id, event_type);
CREATE INDEX idx_recommendation_events_actor_time
    ON recommendation_events (actor_user_id, occurred_at);
CREATE INDEX idx_recommendation_events_mode_time
    ON recommendation_events (mode, occurred_at);

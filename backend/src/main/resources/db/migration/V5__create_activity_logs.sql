CREATE TABLE activity_logs (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users (id),
    user_email  VARCHAR(255) NOT NULL,
    action      VARCHAR(30)  NOT NULL,
    resource    VARCHAR(500),
    ip_address  VARCHAR(45),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_activity_logs_created_at ON activity_logs (created_at DESC);
CREATE INDEX idx_activity_logs_user_id   ON activity_logs (user_id);
CREATE INDEX idx_activity_logs_action    ON activity_logs (action);

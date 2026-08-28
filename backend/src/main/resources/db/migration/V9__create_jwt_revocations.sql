CREATE TABLE jwt_revocations (
    id          BIGSERIAL    PRIMARY KEY,
    token_jti   VARCHAR(64)  NOT NULL UNIQUE,
    user_id     BIGINT       NOT NULL REFERENCES users (id),
    reason      VARCHAR(50)  NOT NULL,
    revoked_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_jwt_revocations_user_id   ON jwt_revocations (user_id);
CREATE INDEX idx_jwt_revocations_revoked_at ON jwt_revocations (revoked_at DESC);
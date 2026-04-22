CREATE TABLE anonymous_sessions (
    token           VARCHAR(64)  NOT NULL,
    fingerprint     VARCHAR(255),
    ip_address      VARCHAR(64),
    messages_used   INT          NOT NULL DEFAULT 0,
    messages_limit  INT          NOT NULL DEFAULT 0,
    tokens_used     INT          NOT NULL DEFAULT 0,
    tokens_limit    INT          NOT NULL DEFAULT 0,
    created_at      DATETIME(6),
    expires_at      DATETIME(6),
    last_used_at    DATETIME(6),
    revoked         BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (token)
);

CREATE INDEX idx_sessions_ip_expires ON anonymous_sessions (ip_address, expires_at);
CREATE INDEX idx_sessions_expires    ON anonymous_sessions (expires_at);

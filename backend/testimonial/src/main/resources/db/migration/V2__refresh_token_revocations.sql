-- V2: revoked refresh tokens.
--
-- Access tokens are stateless JWTs and cannot be revoked on their own, so
-- session length is bounded by a short access-token TTL (15 minutes) plus a
-- long-lived refresh token (7 days).
--
-- Logout must end the session immediately rather than waiting for the refresh
-- token to expire, so the refresh token's id is recorded here. A refresh
-- attempt whose id appears in this table is rejected, which ends the session
-- for good.
--
-- The primary key is the token id, so recording a revocation twice is
-- harmless. Rows are removed once the token they revoke would have expired
-- anyway.
CREATE TABLE revoked_refresh_tokens (
    jti         varchar(255) NOT NULL,
    expires_at  timestamptz  NOT NULL,
    revoked_at  timestamptz  NOT NULL,
    CONSTRAINT pk_revoked_refresh_tokens PRIMARY KEY (jti)
);

-- Lets expired revocations be pruned in bulk.
CREATE INDEX ix_revoked_refresh_tokens_expires_at ON revoked_refresh_tokens (expires_at);

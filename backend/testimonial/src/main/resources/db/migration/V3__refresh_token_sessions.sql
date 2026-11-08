-- V3: issued refresh tokens, so a user's sessions can be ended in bulk.
--
-- A password reset must end the sessions that were opened with the old
-- password, but the revocation table only records tokens that have already
-- been revoked -- it cannot answer "which refresh tokens does this user
-- hold?", and deleting the revocation rows would do the opposite of what is
-- wanted, clearing the blacklist.
--
-- So the refresh tokens that have been issued are recorded here, with the
-- user they belong to. A password reset revokes every row for that user, and
-- each revoked token's id is copied into revoked_refresh_tokens so that the
-- refresh endpoint rejects it.
--
-- A row is removed when the token it describes is refreshed (rotation) or
-- expires, so the table holds only the sessions that are still live.
CREATE TABLE refresh_tokens (
    jti         varchar(255) NOT NULL,
    user_id     uuid         NOT NULL,
    expires_at  timestamptz  NOT NULL,
    created_at  timestamptz  NOT NULL,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (jti),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES app_users (id) ON DELETE CASCADE
);

-- Lets a user's sessions be found and revoked in one query.
CREATE INDEX ix_refresh_tokens_user_id ON refresh_tokens (user_id);

-- Lets expired rows be pruned in bulk.
CREATE INDEX ix_refresh_tokens_expires_at ON refresh_tokens (expires_at);

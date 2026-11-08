-- V1: initial schema for the Testimonial Collection SaaS.
--
-- Flyway owns the schema (PRD 8). Hibernate runs with ddl-auto=validate, so
-- the entity mappings must match this DDL exactly or the app will not boot.
--
-- Column types are chosen to match the Hibernate mappings:
--   smallint  -> Short          (rating)
--   text      -> String, no @Lob (message)
--   jsonb     -> @JdbcTypeCode(SqlTypes.JSON) (form/embed config, social_links)
--   inet      -> InetAddress    (submitter_ip)
--   uuid      -> java.util.UUID
--   timestamptz -> Instant (hibernate.time_zone is UTC)

-- ---------------------------------------------------------------------------
-- app_users
--
-- Named app_users because "users" is a reserved word in PostgreSQL.
--
-- Email uniqueness is case-insensitive (PRD 4.1) but the address is stored as
-- the user typed it, for display. A unique index on lower(email) enforces the
-- rule in the database itself, so a concurrent double-registration cannot slip
-- past an application-level check.
--
-- password_hash is NULL for Google-only accounts. google_subject is Google's
-- stable user id; NULL for accounts that have never signed in with Google.
-- PostgreSQL treats NULLs as distinct in unique indexes, so many password-only
-- accounts can coexist (correct).
-- ---------------------------------------------------------------------------
CREATE TABLE app_users (
    id              uuid         NOT NULL,
    email           varchar(255) NOT NULL,
    password_hash   varchar(255) NULL,
    name            varchar(120) NULL,
    email_verified  boolean      NOT NULL DEFAULT false,
    google_subject  varchar(255) NULL,
    created_at      timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL,
    CONSTRAINT pk_app_users PRIMARY KEY (id)
);

CREATE UNIQUE INDEX ux_app_users_email_lower ON app_users (lower(email));
CREATE UNIQUE INDEX ux_app_users_google_subject ON app_users (google_subject);

-- ---------------------------------------------------------------------------
-- email_verification_tokens / password_reset_tokens
--
-- Separate tables per purpose so a reset token can never be redeemed as a
-- verification link (PRD 4.1). The raw token is never stored: only its hash.
-- Single use is enforced by a conditional UPDATE on used_at/expires_at, which
-- is atomic -- a SELECT followed by an UPDATE would be racy.
-- ---------------------------------------------------------------------------
CREATE TABLE email_verification_tokens (
    id          uuid         NOT NULL,
    user_id     uuid         NOT NULL,
    token_hash  varchar(255) NOT NULL,
    expires_at  timestamptz  NOT NULL,
    used_at     timestamptz  NULL,
    created_at  timestamptz  NOT NULL,
    CONSTRAINT pk_email_verification_tokens PRIMARY KEY (id),
    CONSTRAINT fk_email_verification_tokens_user
        FOREIGN KEY (user_id) REFERENCES app_users (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX ux_email_verification_tokens_hash
    ON email_verification_tokens (token_hash);
CREATE INDEX ix_email_verification_tokens_user
    ON email_verification_tokens (user_id);

CREATE TABLE password_reset_tokens (
    id          uuid         NOT NULL,
    user_id     uuid         NOT NULL,
    token_hash  varchar(255) NOT NULL,
    expires_at  timestamptz  NOT NULL,
    used_at     timestamptz  NULL,
    created_at  timestamptz  NOT NULL,
    CONSTRAINT pk_password_reset_tokens PRIMARY KEY (id),
    CONSTRAINT fk_password_reset_tokens_user
        FOREIGN KEY (user_id) REFERENCES app_users (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX ux_password_reset_tokens_hash
    ON password_reset_tokens (token_hash);
CREATE INDEX ix_password_reset_tokens_user
    ON password_reset_tokens (user_id);

-- ---------------------------------------------------------------------------
-- spaces
--
-- form_config / embed_config / public_profile_fields are jsonb: the shape is
-- owned by the application layer and changes without a migration.
--
-- public_profile_fields is deliberately independent of form_config: it gates
-- which submitter fields an embed may expose (PRD 4.8), which is not the same
-- question as which fields the public form collects (PRD 4.3).
--
-- There is no slug_locked column. "Locked once the Space receives its first
-- submission" is derived from the existence of a testimonial, which avoids
-- state that can drift out of sync.
--
-- There is no read_only column either. After a downgrade the read-only Spaces
-- are derived at read time (OPEN-B): the oldest N by creation date stay
-- editable and the rest are read-only.
--
-- seq is a monotonic identity column used to rank a user's Spaces for that
-- derivation. created_at alone is not enough: timestamptz has microsecond
-- precision, so two Spaces can share a value, and PostgreSQL's sort is not
-- stable -- the same request could otherwise see a different read-only set.
-- ---------------------------------------------------------------------------
CREATE TABLE spaces (
    id                   uuid         NOT NULL,
    seq                  bigint       GENERATED BY DEFAULT AS IDENTITY,
    user_id              uuid         NOT NULL,
    title                varchar(200) NOT NULL,
    subtitle             varchar(500) NULL,
    slug                 varchar(120) NOT NULL,
    theme_key            varchar(50)  NOT NULL,
    form_config          jsonb        NOT NULL,
    embed_config         jsonb        NOT NULL,
    public_profile_fields jsonb       NOT NULL,
    rating_enabled       boolean      NOT NULL DEFAULT false,
    created_at           timestamptz  NOT NULL,
    updated_at           timestamptz  NOT NULL,
    CONSTRAINT pk_spaces PRIMARY KEY (id),
    CONSTRAINT fk_spaces_user
        FOREIGN KEY (user_id) REFERENCES app_users (id) ON DELETE CASCADE
);

-- The public URL /s/{slug} must be unique across the platform (PRD 4.2).
CREATE UNIQUE INDEX ux_spaces_slug ON spaces (slug);

-- Serves the owner's Space list (PRD 4.6) and the OPEN-B read-only ranking.
CREATE INDEX ix_spaces_user_seq ON spaces (user_id, seq);

-- ---------------------------------------------------------------------------
-- testimonials
--
-- A testimonial belongs to exactly one Space, which belongs to exactly one
-- user (PRD 6.2). Deleting a Space permanently deletes its testimonials and
-- its public URL (PRD 4.2), hence ON DELETE CASCADE.
--
-- consent_given is stored but never gates display or Wall of Love inclusion
-- (PRD 4.3, 6.9). A submission with the box unchecked is accepted.
--
-- hidden = false means visible (PRD 4.7 default: visible = yes).
--
-- rating is smallint because it is an integer 1-5; the mapping is Short.
--
-- social_links is jsonb rather than text[]: Hibernate 7 under Spring Boot 4
-- resolves List<String> to a JSON JDBC type, so text[] cannot be mapped.
--
-- There is no deleted_at: deletion is permanent (PRD 4.7), and hard deletion
-- is what keeps deleted testimonials out of dashboard counts (PRD 4.6).
-- ---------------------------------------------------------------------------
CREATE TABLE testimonials (
    id             uuid         NOT NULL,
    space_id       uuid         NOT NULL,
    name           varchar(200) NOT NULL,
    email          varchar(255) NOT NULL,
    message        text         NOT NULL,
    rating         smallint     NULL,
    company_name   varchar(200) NULL,
    job_title      varchar(200) NULL,
    website_url    varchar(500) NULL,
    social_links   jsonb        NULL,
    consent_given  boolean      NOT NULL DEFAULT false,
    favorite       boolean      NOT NULL DEFAULT false,
    wall_of_love   boolean      NOT NULL DEFAULT false,
    hidden         boolean      NOT NULL DEFAULT false,
    submitter_ip   inet         NULL,
    user_agent     varchar(500) NULL,
    created_at     timestamptz  NOT NULL,
    updated_at     timestamptz  NOT NULL,
    CONSTRAINT pk_testimonials PRIMARY KEY (id),
    CONSTRAINT fk_testimonials_space
        FOREIGN KEY (space_id) REFERENCES spaces (id) ON DELETE CASCADE,
    CONSTRAINT ck_testimonials_rating CHECK (rating IS NULL OR rating BETWEEN 1 AND 5)
);

-- The embed shows only Wall of Love AND visible testimonials (PRD 4.8).
-- Partial index so that query stays index-backed as the table grows.
CREATE INDEX ix_testimonials_embed
    ON testimonials (space_id)
    WHERE wall_of_love AND NOT hidden;

-- Serves the inbox ordering, the daily analytics buckets and the
-- "has this Space received its first submission" check.
CREATE INDEX ix_testimonials_space_created
    ON testimonials (space_id, created_at);

-- ---------------------------------------------------------------------------
-- subscriptions
--
-- One row per user. `plan` and `status` are both needed because PRD 4.9
-- requires past_due to KEEP Premium access while a final failure downgrades
-- to Free -- so plan=PREMIUM with status=PAST_DUE is a legitimate state.
--
-- The effective plan is computed in one place from these two columns; the
-- verified webhook is the only writer, so they cannot drift (PRD 6.15).
-- ---------------------------------------------------------------------------
CREATE TABLE subscriptions (
    id                     uuid         NOT NULL,
    user_id                uuid         NOT NULL,
    plan                   varchar(20)  NOT NULL,
    status                 varchar(30)  NOT NULL,
    stripe_customer_id     varchar(255) NULL,
    stripe_subscription_id varchar(255) NULL,
    current_period_end     timestamptz  NULL,
    cancel_at_period_end   boolean      NOT NULL DEFAULT false,
    created_at             timestamptz  NOT NULL,
    updated_at             timestamptz  NOT NULL,
    CONSTRAINT pk_subscriptions PRIMARY KEY (id),
    CONSTRAINT fk_subscriptions_user
        FOREIGN KEY (user_id) REFERENCES app_users (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX ux_subscriptions_user ON subscriptions (user_id);
CREATE UNIQUE INDEX ux_subscriptions_customer ON subscriptions (stripe_customer_id);
CREATE UNIQUE INDEX ux_subscriptions_subscription ON subscriptions (stripe_subscription_id);

-- ---------------------------------------------------------------------------
-- processed_stripe_events
--
-- Records every Stripe event id that has been handled. The event id is the
-- primary key, so replaying the same webhook is a no-op (PRD 4.9, 6.14).
-- ---------------------------------------------------------------------------
CREATE TABLE processed_stripe_events (
    event_id      varchar(255) NOT NULL,
    event_type    varchar(100) NULL,
    processed_at  timestamptz  NOT NULL,
    CONSTRAINT pk_processed_stripe_events PRIMARY KEY (event_id)
);

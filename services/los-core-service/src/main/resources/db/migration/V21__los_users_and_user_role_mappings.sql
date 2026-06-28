-- Local demo users and segment-based role assignments (LOS; not IAM).
CREATE TABLE los_users (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(200) NOT NULL,
    email       VARCHAR(320) NOT NULL,
    mobile      VARCHAR(32),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_los_users_email ON los_users (lower(email));

CREATE TABLE user_role_mappings (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL REFERENCES los_users (id) ON DELETE CASCADE,
    los_role        VARCHAR(50)  NOT NULL,
    loan_product    VARCHAR(80),
    borrower_type   VARCHAR(30),
    min_amount      NUMERIC(15, 2),
    max_amount      NUMERIC(15, 2),
    geography       JSONB,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    priority        INTEGER      NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ
);

CREATE INDEX idx_urm_user ON user_role_mappings (user_id);
CREATE INDEX idx_urm_role_active_pri ON user_role_mappings (los_role, active, priority DESC);

COMMENT ON TABLE los_users IS 'Local LOS directory users (demo) for assignment and UI.';
COMMENT ON TABLE user_role_mappings IS 'Which user is eligible for which product/segment for assignment resolution.';

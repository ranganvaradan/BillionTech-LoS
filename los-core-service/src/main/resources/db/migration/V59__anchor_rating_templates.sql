CREATE TABLE anchor_rating_templates (
    id          UUID         NOT NULL PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    version     INT          NOT NULL DEFAULT 1,
    active      BOOLEAN      NOT NULL DEFAULT FALSE,
    config_json JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ
);

CREATE INDEX idx_anchor_rating_templates_active ON anchor_rating_templates (active, version DESC);

COMMENT ON TABLE anchor_rating_templates IS 'Configurable anchor due-diligence questions, option scores, and rating bands';

-- DP-3 — Policy rule graph + Policy↔Scorecard + scorecard scoring mode
-- Non-destructive. Snapshots, existing policies, and legacy scorecards preserved.
-- Next after V132.

-- 1) Durable Policy rule graph (queryable; GACAT-keyed operands)
CREATE TABLE IF NOT EXISTS ci_policy_rule_graph (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_document_id      UUID NOT NULL REFERENCES ci_policy_document (id) ON DELETE CASCADE,
    policy_version_label    VARCHAR(80) NOT NULL DEFAULT 'DRAFT',
    document_version        INT NOT NULL DEFAULT 1,
    graph_hash              VARCHAR(128) NOT NULL,
    source_snapshot_hash    VARCHAR(128),
    immutable               BOOLEAN NOT NULL DEFAULT FALSE,
    status                  VARCHAR(40) NOT NULL DEFAULT 'MATERIALIZED',
    unresolved_operand_count INT NOT NULL DEFAULT 0,
    rule_count              INT NOT NULL DEFAULT 0,
    metadata                JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ci_policy_rule_graph_doc UNIQUE (policy_document_id, document_version)
);

CREATE INDEX IF NOT EXISTS idx_ci_policy_rule_graph_doc
    ON ci_policy_rule_graph (policy_document_id);

CREATE TABLE IF NOT EXISTS ci_policy_rule_graph_node (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    graph_id                UUID NOT NULL REFERENCES ci_policy_rule_graph (id) ON DELETE CASCADE,
    rule_key                VARCHAR(160) NOT NULL,
    system_rule_id          VARCHAR(160),
    rule_type               VARCHAR(40) NOT NULL DEFAULT 'HARD',
    expression              JSONB NOT NULL DEFAULT '{}'::jsonb,
    on_true                 VARCHAR(40) NOT NULL DEFAULT 'FAIL',
    on_false                VARCHAR(40) NOT NULL DEFAULT 'PASS',
    on_missing              VARCHAR(40) NOT NULL DEFAULT 'DATA_INSUFFICIENT',
    human_wording           TEXT,
    source_snapshot_ref     VARCHAR(200),
    content_hash            VARCHAR(128) NOT NULL,
    sort_order              INT NOT NULL DEFAULT 0,
    metadata                JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ci_policy_rule_graph_node UNIQUE (graph_id, rule_key)
);

CREATE INDEX IF NOT EXISTS idx_ci_policy_rule_graph_node_graph
    ON ci_policy_rule_graph_node (graph_id, sort_order);

CREATE TABLE IF NOT EXISTS ci_policy_rule_graph_operand (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    node_id                 UUID NOT NULL REFERENCES ci_policy_rule_graph_node (id) ON DELETE CASCADE,
    graph_id                UUID NOT NULL REFERENCES ci_policy_rule_graph (id) ON DELETE CASCADE,
    operand_path            VARCHAR(300) NOT NULL,
    original_token          VARCHAR(300) NOT NULL,
    canonical_parameter_id  VARCHAR(200),
    resolution_status       VARCHAR(60) NOT NULL,
    usage_type              VARCHAR(40) NOT NULL DEFAULT 'HARD_RULE',
    required                BOOLEAN NOT NULL DEFAULT TRUE,
    ref_kind                VARCHAR(40) NOT NULL DEFAULT 'METRIC',
    metadata                JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_policy_rule_graph_operand_graph
    ON ci_policy_rule_graph_operand (graph_id);
CREATE INDEX IF NOT EXISTS idx_ci_policy_rule_graph_operand_canon
    ON ci_policy_rule_graph_operand (canonical_parameter_id)
    WHERE canonical_parameter_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_ci_policy_rule_graph_operand_status
    ON ci_policy_rule_graph_operand (resolution_status);

-- 2) Policy Version → optional Scorecard Version (Phase 1: 0..1)
ALTER TABLE ci_policy_document
    ADD COLUMN IF NOT EXISTS scorecard_id UUID NULL;

ALTER TABLE ci_policy_document
    ADD COLUMN IF NOT EXISTS rule_graph_immutable BOOLEAN NOT NULL DEFAULT FALSE;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'fk_ci_policy_document_scorecard'
  ) THEN
    ALTER TABLE ci_policy_document
      ADD CONSTRAINT fk_ci_policy_document_scorecard
      FOREIGN KEY (scorecard_id) REFERENCES underwriting_scorecards (id)
      ON DELETE SET NULL;
  END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_ci_policy_document_scorecard
    ON ci_policy_document (scorecard_id)
    WHERE scorecard_id IS NOT NULL;

-- 3) Scorecard scoring mode + optional reverse Policy link
ALTER TABLE underwriting_scorecards
    ADD COLUMN IF NOT EXISTS scoring_mode VARCHAR(40) NOT NULL DEFAULT 'LEGACY_POINTS_V1';

ALTER TABLE underwriting_scorecards
    ADD COLUMN IF NOT EXISTS policy_document_id UUID NULL;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'fk_underwriting_scorecards_policy_doc'
  ) THEN
    ALTER TABLE underwriting_scorecards
      ADD CONSTRAINT fk_underwriting_scorecards_policy_doc
      FOREIGN KEY (policy_document_id) REFERENCES ci_policy_document (id)
      ON DELETE SET NULL;
  END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_underwriting_scorecards_policy
    ON underwriting_scorecards (policy_document_id)
    WHERE policy_document_id IS NOT NULL;

-- Existing scorecards remain LEGACY_POINTS_V1 (explicit; default already set)
UPDATE underwriting_scorecards
SET scoring_mode = 'LEGACY_POINTS_V1'
WHERE scoring_mode IS NULL OR scoring_mode = '';

COMMENT ON TABLE ci_policy_rule_graph IS
  'DP-3 durable Policy rule graph materialized from session snapshot ruleCandidates (PolicyDsl AST).';
COMMENT ON COLUMN underwriting_scorecards.scoring_mode IS
  'LEGACY_POINTS_V1 (earned/maxPoints) | POLICY_WEIGHTED_V2 (relative weight normalize)';

-- SCORECARD-SAFETY-FOUNDATION-1 — immutability / version lineage / evaluation evidence
ALTER TABLE underwriting_scorecards
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    ADD COLUMN IF NOT EXISTS lineage_id UUID,
    ADD COLUMN IF NOT EXISTS parent_scorecard_id UUID REFERENCES underwriting_scorecards (id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS activated_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS safety_json JSONB NOT NULL DEFAULT '{}'::jsonb;

UPDATE underwriting_scorecards
SET lineage_id = id
WHERE lineage_id IS NULL;

UPDATE underwriting_scorecards
SET status = 'ACTIVE',
    activated_at = COALESCE(activated_at, created_at)
WHERE active = TRUE
  AND status = 'DRAFT';

UPDATE underwriting_scorecards
SET status = 'RETIRED'
WHERE active = FALSE
  AND status = 'DRAFT'
  AND version > 1;

COMMENT ON COLUMN underwriting_scorecards.status IS
    'DRAFT | ACTIVE | RETIRED — ACTIVE rows are immutable for execution-affecting fields';
COMMENT ON COLUMN underwriting_scorecards.lineage_id IS
    'Stable family id across versions; equals id for the first version';
COMMENT ON COLUMN underwriting_scorecards.safety_json IS
    'SCORECARD-SAFETY-FOUNDATION-1: factorPolicies, exclusiveBandMode, validation snapshot';

ALTER TABLE underwriting_evaluations
    ADD COLUMN IF NOT EXISTS scorecard_version INT,
    ADD COLUMN IF NOT EXISTS scorecard_evidence_json JSONB;

COMMENT ON COLUMN underwriting_evaluations.scorecard_evidence_json IS
    'Immutable scoring evidence: bands, provenance, thresholds, hard-rule outcome';

CREATE INDEX IF NOT EXISTS idx_uw_scorecards_lineage
    ON underwriting_scorecards (lineage_id, version DESC);

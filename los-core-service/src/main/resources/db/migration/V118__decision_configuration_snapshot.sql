-- LOS-PRODUCTION-HARDENING-1 — durable immutable decision/configuration snapshot at evaluation time.
-- Prefer JSONB on underwriting_evaluations (no parallel decision engine).

ALTER TABLE underwriting_evaluations
    ADD COLUMN IF NOT EXISTS decision_snapshot_json JSONB;

COMMENT ON COLUMN underwriting_evaluations.decision_snapshot_json IS
    'Immutable routing/GACAT/facts/rules/scorecard/decision snapshot captured at evaluation; do not mutate in place.';

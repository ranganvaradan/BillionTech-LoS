-- SCORECARD-GOVERNANCE-1 — maker-checker lifecycle evidence
-- Status model: DRAFT | IN_REVIEW | APPROVED | ACTIVE | RETIRED
-- Does not change scoring, routing authority, or GACAT bindings.

ALTER TABLE underwriting_scorecards
    ADD COLUMN IF NOT EXISTS governance_json JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE underwriting_scorecards
    ALTER COLUMN status TYPE VARCHAR(32);

COMMENT ON COLUMN underwriting_scorecards.status IS
    'DRAFT | IN_REVIEW | APPROVED | ACTIVE | RETIRED — SCORECARD-GOVERNANCE-1';
COMMENT ON COLUMN underwriting_scorecards.governance_json IS
    'Maker/checker/activation evidence: submittedBy, approvedBy, remarks, lastPreview, supersession, reviewHistory';

-- Existing ACTIVE rows remain ACTIVE (grandfathered). DRAFT stays DRAFT.
-- No silent approval evidence invented.

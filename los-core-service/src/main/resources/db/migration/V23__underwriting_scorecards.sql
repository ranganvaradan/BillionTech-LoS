-- Structured scorecard policies (replaces/augments ad-hoc rules_json for matching segments)
CREATE TABLE underwriting_scorecards (
    id                 UUID         NOT NULL PRIMARY KEY,
    name               VARCHAR(200) NOT NULL,
    borrower_type      VARCHAR(30)  NOT NULL,
    loan_product       VARCHAR(80)  NOT NULL,
    version            INT          NOT NULL DEFAULT 1,
    priority           INT          NOT NULL DEFAULT 0,
    min_amount         NUMERIC(15, 2),
    max_amount         NUMERIC(15, 2),
    geography          JSONB,
    scorecard_json     JSONB        NOT NULL DEFAULT '{}'::jsonb,
    thresholds_json    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    hard_rules_json    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    active             BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ
);

CREATE INDEX idx_uw_scorecards_match ON underwriting_scorecards (borrower_type, loan_product, active, priority DESC);

COMMENT ON TABLE underwriting_scorecards IS 'Policy scorecards: parameters in scorecard_json, cutoffs in thresholds_json, overrides in hard_rules_json';

ALTER TABLE underwriting_evaluations
    ADD COLUMN IF NOT EXISTS scorecard_id UUID REFERENCES underwriting_scorecards (id) ON DELETE SET NULL;

ALTER TABLE underwriting_evaluations
    ADD COLUMN IF NOT EXISTS parameter_results_json JSONB;

COMMENT ON COLUMN underwriting_evaluations.parameter_results_json IS 'Per-parameter scorecard line results and attachment hints';

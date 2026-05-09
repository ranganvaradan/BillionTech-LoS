-- Persists each underwriting run: multi-rule results, effective values, source selection.
CREATE TABLE underwriting_evaluations (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id       UUID         NOT NULL REFERENCES loan_applications (id) ON DELETE CASCADE,
    evaluated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    aggregate_decision   VARCHAR(40)  NOT NULL,
    aggregate_score      INTEGER,
    effective_values_json JSONB       NOT NULL DEFAULT '{}'::jsonb,
    rule_results_json    JSONB        NOT NULL DEFAULT '[]'::jsonb,
    selected_source_json JSONB        NOT NULL DEFAULT '{}'::jsonb,
    evaluated_by         VARCHAR(64),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_uw_eval_app_evaluated ON underwriting_evaluations (application_id, evaluated_at DESC);

COMMENT ON TABLE underwriting_evaluations IS 'Audit trail for underwriting runs (rules, sources, outcomes).';

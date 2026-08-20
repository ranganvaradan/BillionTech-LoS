-- Scoped, auditable manual override of a specific canonical GACAT parameter on one application,
-- for use only when that parameter's real source is confirmed unavailable (e.g. bureau data
-- could not be pulled). Deliberately NOT a blanket bureau-score override — restricted at the
-- application layer to a small allow-list of parameter ids (see ManualParameterOverridePolicy).
CREATE TABLE application_parameter_manual_override (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id           UUID NOT NULL REFERENCES loan_applications(id),
    canonical_parameter_id   VARCHAR(160) NOT NULL,
    value_text               VARCHAR(500) NOT NULL,
    reason                   TEXT,
    entered_by               VARCHAR(120) NOT NULL,
    entered_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    active                   BOOLEAN NOT NULL DEFAULT true,
    superseded_at            TIMESTAMPTZ,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_app_param_override_application
    ON application_parameter_manual_override(application_id);

-- Only one active override per (application, parameter) — mirrors the exact ambiguity failure
-- mode found in the bureau-report idempotency gap (BUREAU_REPORT_AMBIGUOUS); this table must
-- never repeat that mistake.
CREATE UNIQUE INDEX uq_app_param_override_active
    ON application_parameter_manual_override(application_id, canonical_parameter_id)
    WHERE active = true;

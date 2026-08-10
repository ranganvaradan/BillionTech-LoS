-- G0.1 Limited Pilot Certification — extends G0 cutover (never ACTIVE / CANONICAL)

CREATE TABLE IF NOT EXISTS ci_limited_pilot_certification (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cohort_id                       UUID NOT NULL REFERENCES ci_cutover_cohort (id) ON DELETE CASCADE,
    policy_certification_id         UUID,
    decision_certification_id       UUID,
    validation_dataset_summary      JSONB NOT NULL DEFAULT '{}'::jsonb,
    real_stored_case_count          INT NOT NULL DEFAULT 0,
    weighted_evidence_score         NUMERIC(12, 4),
    critical_binding_coverage       NUMERIC(8, 4),
    replay_pass_rate                NUMERIC(8, 4),
    canonical_success_rate          NUMERIC(8, 4),
    canonical_di_rate               NUMERIC(8, 4),
    material_mismatch_rate          NUMERIC(8, 4),
    unresolved_mismatch_count       INT NOT NULL DEFAULT 0,
    security_gate                   BOOLEAN NOT NULL DEFAULT FALSE,
    rollback_gate                   BOOLEAN NOT NULL DEFAULT FALSE,
    operations_gate                 BOOLEAN NOT NULL DEFAULT FALSE,
    status                          VARCHAR(40) NOT NULL DEFAULT 'NOT_READY',
    certified_by                    VARCHAR(120),
    certified_at                    TIMESTAMPTZ,
    notes                           TEXT,
    gate_results                    JSONB NOT NULL DEFAULT '{}'::jsonb,
    blockers                        JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_ci_limited_pilot_status CHECK (
        status IN ('NOT_READY', 'READY_WITH_EXCEPTIONS', 'LIMITED_PILOT_READY', 'REVOKED')
    )
);

CREATE INDEX IF NOT EXISTS idx_ci_limited_pilot_cohort
    ON ci_limited_pilot_certification (cohort_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ci_limited_pilot_status
    ON ci_limited_pilot_certification (status);

CREATE TABLE IF NOT EXISTS ci_pilot_data_gap (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id      UUID,
    cohort_id           UUID NOT NULL REFERENCES ci_cutover_cohort (id) ON DELETE CASCADE,
    canonical_path      VARCHAR(240) NOT NULL,
    cause               VARCHAR(120) NOT NULL,
    remediable          BOOLEAN NOT NULL DEFAULT TRUE,
    required_action     TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_pilot_data_gap_cohort
    ON ci_pilot_data_gap (cohort_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ci_pilot_data_gap_path
    ON ci_pilot_data_gap (canonical_path);

CREATE TABLE IF NOT EXISTS ci_cutover_operational_event (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cohort_id           UUID NOT NULL REFERENCES ci_cutover_cohort (id) ON DELETE CASCADE,
    event_type          VARCHAR(80) NOT NULL,
    detail              JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          VARCHAR(120)
);

CREATE INDEX IF NOT EXISTS idx_ci_cutover_ops_event_cohort
    ON ci_cutover_operational_event (cohort_id, created_at DESC);

CREATE TABLE IF NOT EXISTS ci_cutover_drill (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cohort_id           UUID NOT NULL REFERENCES ci_cutover_cohort (id) ON DELETE CASCADE,
    drill_type          VARCHAR(40) NOT NULL,
    result              VARCHAR(20) NOT NULL,
    evidence            JSONB NOT NULL DEFAULT '{}'::jsonb,
    performed_by        VARCHAR(120),
    performed_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_ci_cutover_drill_type CHECK (drill_type IN ('ROLLBACK', 'KILL_SWITCH')),
    CONSTRAINT chk_ci_cutover_drill_result CHECK (result IN ('PASS', 'FAIL'))
);

CREATE INDEX IF NOT EXISTS idx_ci_cutover_drill_cohort
    ON ci_cutover_drill (cohort_id, performed_at DESC);

CREATE TABLE IF NOT EXISTS ci_cutover_exception (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cohort_id           UUID NOT NULL REFERENCES ci_cutover_cohort (id) ON DELETE CASCADE,
    exception_code      VARCHAR(80) NOT NULL,
    risk                TEXT NOT NULL,
    approver            VARCHAR(120) NOT NULL,
    mitigation          TEXT,
    expiry              TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_cutover_exception_cohort
    ON ci_cutover_exception (cohort_id, created_at DESC);

CREATE TABLE IF NOT EXISTS ci_pilot_candidate_score (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_code        VARCHAR(80) NOT NULL,
    score               NUMERIC(8, 4) NOT NULL,
    rationale           JSONB NOT NULL DEFAULT '{}'::jsonb,
    ranked_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_pilot_candidate_ranked
    ON ci_pilot_candidate_score (ranked_at DESC, score DESC);

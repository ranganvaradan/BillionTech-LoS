-- POLICY-DERIVED-PARAMETER-RESEARCH-AND-AUTHORING-1
-- Advisory research proposals. Executable definitions remain in V139 table only after human Accept.

CREATE TABLE IF NOT EXISTS ci_gacat_derived_calculation_proposal (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID,
    target_parameter_id     VARCHAR(200) NOT NULL,
    target_parameter_name   VARCHAR(300),
    scope                   VARCHAR(40)  NOT NULL DEFAULT 'PLATFORM',
    proposal_status         VARCHAR(40)  NOT NULL DEFAULT 'DRAFT',
    confidence              VARCHAR(20),
    human_explanation       TEXT,
    proposed_expression     JSONB,
    candidate_dependencies  JSONB        NOT NULL DEFAULT '[]'::jsonb,
    assumptions             JSONB        NOT NULL DEFAULT '[]'::jsonb,
    limitations             JSONB        NOT NULL DEFAULT '[]'::jsonb,
    missing_dependencies    JSONB        NOT NULL DEFAULT '[]'::jsonb,
    evidence                JSONB        NOT NULL DEFAULT '[]'::jsonb,
    alternatives            JSONB        NOT NULL DEFAULT '[]'::jsonb,
    option_index            INTEGER      NOT NULL DEFAULT 1,
    recommended             BOOLEAN      NOT NULL DEFAULT FALSE,
    created_by              VARCHAR(120),
    approved_by             VARCHAR(120),
    rejected_by             VARCHAR(120),
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    approved_at             TIMESTAMPTZ,
    rejected_at             TIMESTAMPTZ,
    resulting_definition_id UUID,
    metadata                JSONB        NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT ck_derived_calc_proposal_scope CHECK (scope IN ('PLATFORM', 'LENDER')),
    CONSTRAINT ck_derived_calc_proposal_status CHECK (proposal_status IN (
        'DRAFT', 'READY_FOR_REVIEW', 'NEEDS_INPUT', 'APPROVED', 'REJECTED', 'SUPERSEDED'))
);

CREATE INDEX IF NOT EXISTS idx_derived_calc_proposal_target
    ON ci_gacat_derived_calculation_proposal (target_parameter_id, proposal_status);

CREATE INDEX IF NOT EXISTS idx_derived_calc_proposal_tenant
    ON ci_gacat_derived_calculation_proposal (tenant_id, target_parameter_id);

COMMENT ON TABLE ci_gacat_derived_calculation_proposal IS
    'Advisory derivation research. Never executable until Accept creates a V139 definition.';

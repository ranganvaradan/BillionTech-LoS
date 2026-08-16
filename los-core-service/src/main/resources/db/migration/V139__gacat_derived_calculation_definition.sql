-- POLICY-GRAPH-AND-DERIVED-PARAMETER-CLOSURE-1
-- Safe derived calculation definitions (platform or lender-scoped).
-- Does not invent executable code; stores typed expression JSON over exact GACAT inputs.

CREATE TABLE IF NOT EXISTS ci_gacat_derived_calculation_definition (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID,
    canonical_parameter_id  VARCHAR(200) NOT NULL,
    scope                   VARCHAR(40)  NOT NULL DEFAULT 'PLATFORM',
    status                  VARCHAR(40)  NOT NULL DEFAULT 'DEFINED',
    result_type             VARCHAR(40)  NOT NULL DEFAULT 'NUMBER',
    unit                    VARCHAR(40),
    description             TEXT,
    expression_json         JSONB        NOT NULL DEFAULT '{}'::jsonb,
    dependency_ids          JSONB        NOT NULL DEFAULT '[]'::jsonb,
    version_no              INTEGER      NOT NULL DEFAULT 1,
    created_by              VARCHAR(120),
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    metadata                JSONB        NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT ck_derived_calc_scope CHECK (scope IN ('PLATFORM', 'LENDER')),
    CONSTRAINT ck_derived_calc_status CHECK (status IN ('DEFINED', 'TESTED', 'PRODUCTION_READY', 'RETIRED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_derived_calc_platform_param_ver
    ON ci_gacat_derived_calculation_definition (canonical_parameter_id, version_no)
    WHERE tenant_id IS NULL AND scope = 'PLATFORM';

CREATE UNIQUE INDEX IF NOT EXISTS uq_derived_calc_lender_param_ver
    ON ci_gacat_derived_calculation_definition (tenant_id, canonical_parameter_id, version_no)
    WHERE tenant_id IS NOT NULL AND scope = 'LENDER';

CREATE INDEX IF NOT EXISTS idx_derived_calc_canonical
    ON ci_gacat_derived_calculation_definition (canonical_parameter_id, status);

COMMENT ON TABLE ci_gacat_derived_calculation_definition IS
    'Typed safe expressions for GACAT derived parameters. PLATFORM = BillionTech; LENDER = tenant-scoped.';

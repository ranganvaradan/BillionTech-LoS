-- Canonical LOS product -> external system (Encore) product mapping authority.
-- This replaces "workflow_configs.lms_product_code" as the final ordinary authority.

CREATE TABLE IF NOT EXISTS external_product_mapping (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    los_product_code VARCHAR(100) NOT NULL,
    external_system VARCHAR(30) NOT NULL,
    external_product_code VARCHAR(100) NOT NULL,
    version INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    effective_from DATE NOT NULL,
    effective_to DATE NOT NULL,
    metadata_json JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_external_product_mapping_business
        UNIQUE (los_product_code, external_system, version)
);

-- Effective lookup performance.
CREATE INDEX IF NOT EXISTS idx_external_product_mapping_los_sys_eff
    ON external_product_mapping (los_product_code, external_system, effective_from, effective_to);

CREATE INDEX IF NOT EXISTS idx_external_product_mapping_status
    ON external_product_mapping (status);

-- Safe migration (seed) from existing workflow_lms_product_mapping:
-- Only migrate LOS products where the encore_product_code is identical across all borrower_type rows.
-- Do not migrate invoice-discounting (handled via ProgramMaster-specific mapping).
INSERT INTO external_product_mapping (
    los_product_code,
    external_system,
    external_product_code,
    version,
    status,
    effective_from,
    effective_to,
    metadata_json
)
SELECT
    w.loan_product,
    'ENCORE',
    MIN(w.encore_product_code) AS external_product_code,
    1 AS version,
    'ACTIVE' AS status,
    DATE '2000-01-01' AS effective_from,
    DATE '9999-12-31' AS effective_to,
    jsonb_build_object(
        'source', 'workflow_lms_product_mapping',
        'distinctBorrowerTypes', COUNT(DISTINCT w.borrower_type)
    ) AS metadata_json
FROM workflow_lms_product_mapping w
WHERE w.loan_product <> 'BUSINESS_WC_INVOICE_DISCOUNTING'
GROUP BY w.loan_product
HAVING COUNT(DISTINCT w.encore_product_code) = 1
ON CONFLICT (los_product_code, external_system, version) DO NOTHING;

-- Persist pinned mapping on application to guarantee retry/idempotency.
ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS external_product_mapping_id UUID;

ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS external_product_mapping_version INTEGER;

COMMENT ON COLUMN loan_applications.external_product_mapping_id IS
    'Pinned canonical LOS->Encore external product mapping id (resolved before sanction/open-account).';

COMMENT ON COLUMN loan_applications.external_product_mapping_version IS
    'Pinned canonical LOS->Encore external product mapping version.';

-- Also persist onto LMS handover for easier certification evidence correlation.
ALTER TABLE lms_loan_handover
    ADD COLUMN IF NOT EXISTS external_product_mapping_id UUID;

ALTER TABLE lms_loan_handover
    ADD COLUMN IF NOT EXISTS external_product_mapping_version INTEGER;

COMMENT ON COLUMN lms_loan_handover.external_product_mapping_id IS
    'Pinned canonical LOS->Encore external product mapping id used for openLoanAccount payload.';

COMMENT ON COLUMN lms_loan_handover.external_product_mapping_version IS
    'Pinned canonical LOS->Encore external product mapping version used for openLoanAccount payload.';


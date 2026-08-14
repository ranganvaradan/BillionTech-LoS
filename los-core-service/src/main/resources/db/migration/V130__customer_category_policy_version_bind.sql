-- LOS-CREDIT-ARCHITECTURE-RECONCILIATION-STEP2
-- Customer Category → Policy Studio Policy Version bind (config only; no live routing).
-- Reuses ci_policy_applicability / ci_policy_document — does not create a duplicate Policy model.
-- policy_set remains transitional/internal; no longer required for new Categories.

ALTER TABLE customer_category
    ALTER COLUMN policy_set_id DROP NOT NULL;

ALTER TABLE customer_category
    ADD COLUMN IF NOT EXISTS policy_applicability_id UUID
        REFERENCES ci_policy_applicability (id),
    ADD COLUMN IF NOT EXISTS policy_document_id UUID
        REFERENCES ci_policy_document (id),
    ADD COLUMN IF NOT EXISTS policy_version_label VARCHAR(40),
    ADD COLUMN IF NOT EXISTS policy_lineage_id UUID;

CREATE INDEX IF NOT EXISTS ix_customer_category_policy_app
    ON customer_category (policy_applicability_id);
CREATE INDEX IF NOT EXISTS ix_customer_category_policy_doc
    ON customer_category (policy_document_id);

COMMENT ON COLUMN customer_category.policy_set_id IS
    'TRANSITIONAL / INTERNAL — legacy Policy Set package. Nullable; not lender-facing principal underwriting bind.';
COMMENT ON COLUMN customer_category.policy_applicability_id IS
    'Principal underwriting bind: exact Policy Studio catalogue row (Policy Version). Config only; not live UW.';
COMMENT ON COLUMN customer_category.policy_document_id IS
    'Policy Studio document identity for the bound version (denormalised from applicability).';
COMMENT ON COLUMN customer_category.policy_version_label IS
    'Exact Policy Version label (e.g. v1) pinned at Category bind time.';
COMMENT ON COLUMN customer_category.policy_lineage_id IS
    'Stable Policy lineage handle across versions (optional; from Policy Studio metadata when available).';

COMMENT ON TABLE customer_category IS
    'Customer Category lending proposition. Principal underwriting relation = Policy Studio Policy Version via policy_applicability_id. policy_set_id transitional only. LIVE UW not wired.';

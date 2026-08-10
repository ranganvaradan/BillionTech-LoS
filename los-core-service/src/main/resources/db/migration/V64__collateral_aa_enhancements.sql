-- Collateral & Account Aggregator enhancements (COLLATERAL_AA_SPEC Part 4).
-- Note: V20 is already assigned to assignment_rule_sets; this migration uses the next available version.

-- Add collateral / AA summary columns on loan applications
ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS collateral_ltv_ratio DECIMAL(5, 2),
    ADD COLUMN IF NOT EXISTS collateral_total_value DECIMAL(15, 2),
    ADD COLUMN IF NOT EXISTS aa_consent_status VARCHAR(30);

-- Faster AA lookups by customer
CREATE INDEX IF NOT EXISTS idx_aa_consent_customer ON aa_consents (customer_id);

-- Persist AA-derived metrics on underwriting evaluation runs
ALTER TABLE underwriting_evaluations
    ADD COLUMN IF NOT EXISTS aa_data_summary JSONB,
    ADD COLUMN IF NOT EXISTS foir_from_aa DECIMAL(5, 2);

COMMENT ON COLUMN loan_applications.collateral_ltv_ratio IS 'Latest LTV ratio (%) from completed collateral valuations';
COMMENT ON COLUMN loan_applications.collateral_total_value IS 'Total accepted collateral value (INR) used for LTV';
COMMENT ON COLUMN loan_applications.aa_consent_status IS 'Latest AA consent status snapshot (PENDING, APPROVED, DATA_FETCHED, REVOKED)';
COMMENT ON COLUMN underwriting_evaluations.aa_data_summary IS 'Condensed Account Aggregator fetch summary used in underwriting';
COMMENT ON COLUMN underwriting_evaluations.foir_from_aa IS 'FOIR (%) derived from AA regular EMI outflows vs avg monthly inflow';

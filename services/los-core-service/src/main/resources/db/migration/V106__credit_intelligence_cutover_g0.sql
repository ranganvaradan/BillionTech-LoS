-- G0 Production Cutover Readiness — dual-run / quarantine / certification (never ACTIVE / CANONICAL)

CREATE TABLE IF NOT EXISTS ci_legacy_default_definition (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legacy_key                      VARCHAR(120) NOT NULL,
    component                       VARCHAR(120) NOT NULL,
    file_path                       VARCHAR(400),
    method_name                     VARCHAR(120),
    default_value                   VARCHAR(120),
    value_type                      VARCHAR(40),
    trigger_condition               TEXT,
    products                        JSONB NOT NULL DEFAULT '[]'::jsonb,
    tenants                         JSONB NOT NULL DEFAULT '[]'::jsonb,
    rules_impacted                  JSONB NOT NULL DEFAULT '[]'::jsonb,
    scorecards_impacted             JSONB NOT NULL DEFAULT '[]'::jsonb,
    decision_dimensions_impacted    JSONB NOT NULL DEFAULT '[]'::jsonb,
    severity                        VARCHAR(40) NOT NULL DEFAULT 'HIGH',
    classification                  VARCHAR(60) NOT NULL,
    canonical_replacement           VARCHAR(200),
    missing_data_behavior           VARCHAR(40),
    status                          VARCHAR(40) NOT NULL DEFAULT 'DISCOVERED',
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ci_legacy_default_key_comp UNIQUE (legacy_key, component)
);

CREATE INDEX IF NOT EXISTS idx_ci_legacy_default_status ON ci_legacy_default_definition (status);
CREATE INDEX IF NOT EXISTS idx_ci_legacy_default_class ON ci_legacy_default_definition (classification);

CREATE TABLE IF NOT EXISTS ci_cutover_cohort (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                       UUID NOT NULL,
    lender_id                       UUID,
    product_code                    VARCHAR(80) NOT NULL,
    segment                         VARCHAR(120),
    effective_from                  TIMESTAMPTZ,
    status                          VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    canonical_policy_package_id     UUID,
    decision_strategy_id            UUID,
    binding_set_version             VARCHAR(80),
    rollback_policy                 JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by                      VARCHAR(120),
    approved_by                     VARCHAR(120),
    metadata                        JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_ci_cutover_cohort_status CHECK (
        status IN ('DRAFT', 'VALIDATION', 'DUAL_RUN', 'READY', 'ACTIVE', 'ROLLED_BACK', 'SUSPENDED')
    )
);

CREATE INDEX IF NOT EXISTS idx_ci_cutover_cohort_tenant ON ci_cutover_cohort (tenant_id, product_code);
CREATE INDEX IF NOT EXISTS idx_ci_cutover_cohort_status ON ci_cutover_cohort (status);

CREATE TABLE IF NOT EXISTS ci_cutover_dimension (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cohort_id           UUID NOT NULL REFERENCES ci_cutover_cohort (id) ON DELETE CASCADE,
    dimension_code      VARCHAR(40) NOT NULL,
    readiness           VARCHAR(40) NOT NULL DEFAULT 'NOT_READY',
    authority_source    VARCHAR(40) NOT NULL DEFAULT 'LEGACY',
    notes               TEXT,
    CONSTRAINT uq_ci_cutover_dimension UNIQUE (cohort_id, dimension_code)
);

CREATE INDEX IF NOT EXISTS idx_ci_cutover_dimension_cohort ON ci_cutover_dimension (cohort_id);

CREATE TABLE IF NOT EXISTS ci_cutover_comparison (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cohort_id                   UUID NOT NULL REFERENCES ci_cutover_cohort (id) ON DELETE CASCADE,
    application_id              UUID,
    evaluation_context_id       UUID,
    legacy_policy_outcome       VARCHAR(40),
    canonical_policy_outcome    VARCHAR(40),
    legacy_amount               NUMERIC(18, 2),
    canonical_amount            NUMERIC(18, 2),
    legacy_tenure               INT,
    canonical_tenure            INT,
    legacy_pricing              NUMERIC(12, 6),
    canonical_pricing           NUMERIC(12, 6),
    legacy_authority            VARCHAR(80),
    canonical_authority         VARCHAR(80),
    legacy_conditions           JSONB NOT NULL DEFAULT '[]'::jsonb,
    canonical_conditions        JSONB NOT NULL DEFAULT '[]'::jsonb,
    comparison_class            VARCHAR(60) NOT NULL,
    materiality                 VARCHAR(40),
    root_cause                  VARCHAR(60),
    reason_codes                JSONB NOT NULL DEFAULT '[]'::jsonb,
    decision_trace              JSONB NOT NULL DEFAULT '{}'::jsonb,
    review_status               VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    reviewed_by                 VARCHAR(120),
    review_disposition          VARCHAR(60),
    review_comment              TEXT,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_cutover_comparison_cohort ON ci_cutover_comparison (cohort_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ci_cutover_comparison_app ON ci_cutover_comparison (application_id);

CREATE TABLE IF NOT EXISTS ci_cutover_readiness_snapshot (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cohort_id               UUID REFERENCES ci_cutover_cohort (id) ON DELETE SET NULL,
    overall_outcome         VARCHAR(40) NOT NULL,
    score                   NUMERIC(8, 4),
    dimensions              JSONB NOT NULL DEFAULT '{}'::jsonb,
    blockers                JSONB NOT NULL DEFAULT '[]'::jsonb,
    limited_pilot_ready     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_cutover_readiness_cohort ON ci_cutover_readiness_snapshot (cohort_id, created_at DESC);

CREATE TABLE IF NOT EXISTS ci_policy_certification (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    policy_package_id   UUID NOT NULL,
    status              VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    certified_by        VARCHAR(120),
    certified_at        TIMESTAMPTZ,
    evidence_refs       JSONB NOT NULL DEFAULT '[]'::jsonb,
    checklist           JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_policy_cert_tenant ON ci_policy_certification (tenant_id);

CREATE TABLE IF NOT EXISTS ci_decision_certification (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    decision_strategy_id    UUID NOT NULL,
    status                  VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    certified_by            VARCHAR(120),
    certified_at            TIMESTAMPTZ,
    evidence_refs           JSONB NOT NULL DEFAULT '[]'::jsonb,
    checklist               JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_decision_cert_tenant ON ci_decision_certification (tenant_id);

CREATE TABLE IF NOT EXISTS ci_cutover_control (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cohort_id           UUID NOT NULL REFERENCES ci_cutover_cohort (id) ON DELETE CASCADE,
    authority_mode      VARCHAR(40) NOT NULL,
    effective_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    changed_by          VARCHAR(120),
    reason              TEXT,
    audit               JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_ci_cutover_control_mode CHECK (authority_mode IN ('LEGACY', 'DUAL_RUN', 'CANONICAL'))
);

CREATE INDEX IF NOT EXISTS idx_ci_cutover_control_cohort ON ci_cutover_control (cohort_id, effective_at DESC);

CREATE TABLE IF NOT EXISTS ci_cutover_review (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    comparison_id       UUID NOT NULL REFERENCES ci_cutover_comparison (id) ON DELETE CASCADE,
    disposition         VARCHAR(60) NOT NULL,
    commentary          TEXT,
    reviewer            VARCHAR(120),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_cutover_review_cmp ON ci_cutover_review (comparison_id);

-- Binding certification extension (C6 table)
ALTER TABLE ci_policy_binding
    ADD COLUMN IF NOT EXISTS certification_status VARCHAR(40) DEFAULT 'UNMAPPED',
    ADD COLUMN IF NOT EXISTS certified_by VARCHAR(120),
    ADD COLUMN IF NOT EXISTS certified_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS evidence_refs JSONB DEFAULT '[]'::jsonb;

-- Seed known legacy defaults from inventory (CreditControlService gap / demo path)
INSERT INTO ci_legacy_default_definition (
    legacy_key, component, file_path, method_name, default_value, value_type, trigger_condition,
    products, rules_impacted, severity, classification, canonical_replacement, missing_data_behavior, status
) VALUES
('MONTHLY_INCOME', 'CreditControlService', 'com/los/core/service/credit/CreditControlService.java',
 'applyMissingScorecardDefaults', '85000', 'DECIMAL', 'scorecard missing monthly income',
 '["ALL"]'::jsonb, '["FOIR","DTI"]'::jsonb, 'CRITICAL', 'UNSAFE_SILENT_DEFAULT',
 'metric:income.monthly', 'DATA_INSUFFICIENT', 'MAPPED'),
('EMI_OBLIGATION', 'CreditControlService', 'com/los/core/service/credit/CreditControlService.java',
 'applyMissingScorecardDefaults', '15000', 'DECIMAL', 'scorecard missing EMI',
 '["ALL"]'::jsonb, '["FOIR","OBLIGATION_RATIO"]'::jsonb, 'CRITICAL', 'UNSAFE_SILENT_DEFAULT',
 'recon:XSRC_BUREAU_BANK_OBLIGATION', 'DATA_INSUFFICIENT', 'MAPPED'),
('AVERAGE_BANK_BALANCE', 'CreditControlService', 'com/los/core/service/credit/CreditControlService.java',
 'applyMissingScorecardDefaults', '120000', 'DECIMAL', 'scorecard missing ABB',
 '["ALL"]'::jsonb, '["ABB_RULE"]'::jsonb, 'CRITICAL', 'UNSAFE_SILENT_DEFAULT',
 'metric:bank.abb.average', 'DATA_INSUFFICIENT', 'MAPPED'),
('OBLIGATION_RATIO', 'CreditControlService', 'com/los/core/service/credit/CreditControlService.java',
 'applyMissingScorecardDefaults', '25', 'DECIMAL', 'FOIR missing',
 '["ALL"]'::jsonb, '["FOIR"]'::jsonb, 'CRITICAL', 'UNSAFE_SILENT_DEFAULT',
 'metric:obligation.ratio', 'DATA_INSUFFICIENT', 'MAPPED'),
('DTI_RATIO', 'CreditControlService', 'com/los/core/service/credit/CreditControlService.java',
 'applyMissingScorecardDefaults', '18', 'DECIMAL', 'DTI missing',
 '["ALL"]'::jsonb, '["DTI"]'::jsonb, 'CRITICAL', 'UNSAFE_SILENT_DEFAULT',
 'metric:dti.ratio', 'DATA_INSUFFICIENT', 'MAPPED'),
('LIVE_UNSECURED_LOAN_COUNT', 'CreditControlService', 'com/los/core/service/credit/CreditControlService.java',
 'resolveEffective', '2', 'INTEGER', 'bureau live unsecured absent',
 '["ALL"]'::jsonb, '["LIVE_UNSECURED_HARD_RULE"]'::jsonb, 'CRITICAL', 'UNSAFE_SILENT_DEFAULT',
 'metric:bureau.live_unsecured_count', 'DATA_INSUFFICIENT', 'MAPPED'),
('ANNUAL_GST_TURNOVER', 'CreditControlService', 'com/los/core/service/credit/CreditControlService.java',
 'applyMissingScorecardDefaults', '52000000', 'DECIMAL', 'SCF GST turnover gap',
 '["SCF","STARTER","DIGILEAP"]'::jsonb, '["SCF_TURNOVER"]'::jsonb, 'CRITICAL', 'UNSAFE_SILENT_DEFAULT',
 'metric:gst.turnover.trailing_12m', 'DATA_INSUFFICIENT', 'MAPPED'),
('ANNUAL_BANKING_TURNOVER', 'CreditControlService', 'com/los/core/service/credit/CreditControlService.java',
 'applyMissingScorecardDefaults', '41000000', 'DECIMAL', 'SCF banking turnover gap',
 '["SCF","STARTER","DIGILEAP"]'::jsonb, '["SCF_TURNOVER"]'::jsonb, 'CRITICAL', 'UNSAFE_SILENT_DEFAULT',
 'metric:bank.turnover.trailing_12m', 'DATA_INSUFFICIENT', 'MAPPED'),
('ITR_INCOME', 'CreditControlService', 'com/los/core/service/credit/CreditControlService.java',
 'applyMissingScorecardDefaults', '450000', 'DECIMAL', 'SCF ITR income gap',
 '["SCF","STARTER","DIGILEAP"]'::jsonb, '["INCOME_RULE"]'::jsonb, 'CRITICAL', 'UNSAFE_SILENT_DEFAULT',
 'metric:itr.income.total', 'DATA_INSUFFICIENT', 'MAPPED'),
('PAT', 'CreditControlService', 'com/los/core/service/credit/CreditControlService.java',
 'applyMissingScorecardDefaults', '500000', 'DECIMAL', 'SCF PAT gap',
 '["SCF"]'::jsonb, '["SCF_FINANCIALS"]'::jsonb, 'MEDIUM', 'UNSAFE_SILENT_DEFAULT',
 'metric:itr.pat', 'DATA_INSUFFICIENT', 'MAPPED'),
('TOL', 'CreditControlService', 'com/los/core/service/credit/CreditControlService.java',
 'applyMissingScorecardDefaults', '3500000', 'DECIMAL', 'TOL gap',
 '["ALL"]'::jsonb, '["LEVERAGE"]'::jsonb, 'MEDIUM', 'UNSAFE_SILENT_DEFAULT',
 'metric:financials.tol', 'DATA_INSUFFICIENT', 'MAPPED'),
('TNW', 'CreditControlService', 'com/los/core/service/credit/CreditControlService.java',
 'applyMissingScorecardDefaults', '5000000', 'DECIMAL', 'TNW gap',
 '["ALL"]'::jsonb, '["LEVERAGE"]'::jsonb, 'MEDIUM', 'UNSAFE_SILENT_DEFAULT',
 'metric:financials.tnw', 'DATA_INSUFFICIENT', 'MAPPED'),
('BUREAU_ENQUIRIES_3M', 'CreditControlService', 'com/los/core/service/credit/CreditControlService.java',
 'applyMissingScorecardDefaults', '5', 'INTEGER', 'bureau enquiries gap',
 '["ALL"]'::jsonb, '["ENQUIRY_RULE"]'::jsonb, 'MEDIUM', 'UNSAFE_SILENT_DEFAULT',
 'metric:bureau.enquiries_3m', 'DATA_INSUFFICIENT', 'MAPPED'),
('PROVIDER_GAP_DEFAULT_ACTIVE', 'CreditControlService', 'com/los/core/service/credit/CreditControlService.java',
 'applyMissingScorecardDefaults', '1', 'FLAG', 'any gap default applied',
 '["ALL"]'::jsonb, '["ALL_GAP_DEPENDENT"]'::jsonb, 'CRITICAL', 'UNSAFE_SILENT_DEFAULT',
 NULL, 'DATA_INSUFFICIENT', 'DISCOVERED'),
('DEMO_FALLBACK_ACTIVE', 'CreditControlService', 'com/los/core/service/credit/CreditControlService.java',
 'resolveEffective', '1', 'FLAG', 'demo fallback path',
 '["ALL"]'::jsonb, '["BUREAU_DEMO","KYC_DEMO","INCOME_DEMO"]'::jsonb, 'CRITICAL', 'DEMO_ONLY',
 NULL, 'REFER', 'DISCOVERED'),
('avgDailyBalance3m', 'CreditControlService', 'com/los/core/service/credit/CreditControlService.java',
 'applyMissingScorecardDefaults', '120000', 'DECIMAL', 'bank analytics gap',
 '["ALL"]'::jsonb, '["BANK_ANALYTICS"]'::jsonb, 'MEDIUM', 'UNSAFE_SILENT_DEFAULT',
 'metric:bank.abb.average', 'DATA_INSUFFICIENT', 'MAPPED'),
('INTEREST_COVERAGE', 'CreditControlService', 'com/los/core/service/credit/CreditControlService.java',
 'applyMissingScorecardDefaults', '1.6', 'DECIMAL', 'SCF interest coverage gap',
 '["SCF"]'::jsonb, '["SCF_FINANCIALS"]'::jsonb, 'MEDIUM', 'UNSAFE_SILENT_DEFAULT',
 'metric:financials.interest_coverage', 'DATA_INSUFFICIENT', 'MAPPED'),
('DEBT_TO_EQUITY', 'CreditControlService', 'com/los/core/service/credit/CreditControlService.java',
 'applyMissingScorecardDefaults', '1.5', 'DECIMAL', 'SCF D/E gap',
 '["SCF"]'::jsonb, '["SCF_FINANCIALS"]'::jsonb, 'MEDIUM', 'UNSAFE_SILENT_DEFAULT',
 'metric:financials.dte', 'DATA_INSUFFICIENT', 'MAPPED'),
('PRICING_FLOOR_BPS', 'DecisionStrategy', 'com/los/core/creditintelligence/decision/service/PricingEngine.java',
 'price', '1200', 'BPS', 'explicit strategy floor when configured',
 '["ALL"]'::jsonb, '["PRICING"]'::jsonb, 'LOW', 'BUSINESS_POLICY_DEFAULT',
 'strategy:pricing.floorBps', 'NOT_APPLICABLE', 'MAPPED')
ON CONFLICT (legacy_key, component) DO NOTHING;

-- Candidate cohort: VALIDATION (never ACTIVE). Hybrid dimensions.
INSERT INTO ci_cutover_cohort (
    id, tenant_id, product_code, segment, effective_from, status,
    binding_set_version, rollback_policy, created_by, metadata
) VALUES (
    'a1060000-0000-4000-8000-000000000001',
    '00000000-0000-0000-0000-000000000001',
    'DIGILEAP',
    'G0_NARROW_PILOT',
    now(),
    'VALIDATION',
    'G0_BINDING_SET_V1',
    '{"killSwitch":"LEGACY","retainComparisons":true,"redeployNotRequired":true}'::jsonb,
    'system',
    '{"label":"G0_CANDIDATE_COHORT_V1","productAliases":["SCF_STARTER","DIGILEAP"],"authorityMode":"LEGACY"}'::jsonb
) ON CONFLICT DO NOTHING;

INSERT INTO ci_cutover_dimension (cohort_id, dimension_code, readiness, authority_source, notes) VALUES
('a1060000-0000-4000-8000-000000000001', 'SOURCE', 'READY', 'CANONICAL', 'Shadow canonicalization available'),
('a1060000-0000-4000-8000-000000000001', 'FACT', 'READY', 'CANONICAL', 'Fact snapshots shadow-ready'),
('a1060000-0000-4000-8000-000000000001', 'METRIC', 'READY', 'CANONICAL', 'Metric result sets shadow-ready'),
('a1060000-0000-4000-8000-000000000001', 'RECONCILIATION', 'READY', 'CANONICAL', 'Recon shadow-ready'),
('a1060000-0000-4000-8000-000000000001', 'POLICY', 'READY', 'CANONICAL', 'P1 shadow policy candidate'),
('a1060000-0000-4000-8000-000000000001', 'SCORECARD', 'READY', 'CANONICAL', 'Scorecard DSL candidate'),
('a1060000-0000-4000-8000-000000000001', 'LIMIT', 'LEGACY', 'LEGACY', 'Hybrid — remain legacy in G0'),
('a1060000-0000-4000-8000-000000000001', 'PRICING', 'LEGACY', 'LEGACY', 'Hybrid — remain legacy in G0'),
('a1060000-0000-4000-8000-000000000001', 'TENURE', 'LEGACY', 'LEGACY', 'Hybrid — remain legacy in G0'),
('a1060000-0000-4000-8000-000000000001', 'COLLATERAL', 'LEGACY', 'LEGACY', 'Hybrid — remain legacy in G0'),
('a1060000-0000-4000-8000-000000000001', 'AUTHORITY', 'LEGACY', 'LEGACY', 'Never CANONICAL in G0'),
('a1060000-0000-4000-8000-000000000001', 'CONDITIONS', 'NOT_READY', 'LEGACY', 'Await dual-run review')
ON CONFLICT DO NOTHING;

INSERT INTO ci_cutover_control (cohort_id, authority_mode, changed_by, reason, audit) VALUES
('a1060000-0000-4000-8000-000000000001', 'LEGACY', 'system', 'G0 seed — production remains LEGACY',
 '{"g0":true,"allowCanonical":false}'::jsonb);

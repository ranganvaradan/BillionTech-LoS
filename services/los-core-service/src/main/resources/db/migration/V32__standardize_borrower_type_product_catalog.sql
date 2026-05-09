-- V32: Map legacy loan product / borrower labels to master codes; seed 32 default workflows, rule sets, and scorecards.

-- ─── Map function (dropped at end) ─────────────────────────────────────────
CREATE OR REPLACE FUNCTION _v32_map_loan_product(p_raw text) RETURNS text
LANGUAGE plpgsql
IMMUTABLE
AS
$fn$
DECLARE
    t text;
BEGIN
    t := BTRIM(p_raw);
    IF t = '' THEN
        RETURN p_raw;
    END IF;
    RETURN CASE t
        WHEN 'Personal' THEN 'PERSONAL_LOAN'
        WHEN 'Personal Loan' THEN 'PERSONAL_LOAN'
        WHEN 'PERSONAL_LOAN' THEN 'PERSONAL_LOAN'
        WHEN 'Business Term Loan' THEN 'BUSINESS_TERM_LOAN'
        WHEN 'BUSINESS_LOAN' THEN 'BUSINESS_TERM_LOAN'
        WHEN 'BUSINESS_TERM_LOAN' THEN 'BUSINESS_TERM_LOAN'
        WHEN 'SME' THEN 'BUSINESS_TERM_LOAN'
        WHEN 'MSME' THEN 'BUSINESS_TERM_LOAN'
        WHEN 'WORKING_CAPITAL' THEN 'BUSINESS_WC_OD'
        WHEN 'Business Working Capital Loan -- Overdraft' THEN 'BUSINESS_WC_OD'
        WHEN 'BUSINESS_WC_OD' THEN 'BUSINESS_WC_OD'
        WHEN 'Business Working Capital Loan -- Invoice Discounting' THEN 'BUSINESS_WC_INVOICE_DISCOUNTING'
        WHEN 'BUSINESS_WC_INVOICE_DISCOUNTING' THEN 'BUSINESS_WC_INVOICE_DISCOUNTING'
        WHEN 'Term Loan' THEN 'TERM_LOAN'
        WHEN 'TERM_LOAN' THEN 'TERM_LOAN'
        WHEN 'LAP' THEN 'LOAN_AGAINST_PROPERTY'
        WHEN 'Loan Against Property' THEN 'LOAN_AGAINST_PROPERTY'
        WHEN 'LOAN_AGAINST_PROPERTY' THEN 'LOAN_AGAINST_PROPERTY'
        WHEN 'Loan Against Shares' THEN 'LOAN_AGAINST_SECURITIES'
        WHEN 'LAS' THEN 'LOAN_AGAINST_SECURITIES'
        WHEN 'Loan Against Securities' THEN 'LOAN_AGAINST_SECURITIES'
        WHEN 'LOAN_AGAINST_SECURITIES' THEN 'LOAN_AGAINST_SECURITIES'
        WHEN 'Gold Loan' THEN 'LOAN_AGAINST_GOLD'
        WHEN 'Loan Against Gold' THEN 'LOAN_AGAINST_GOLD'
        WHEN 'LOAN_AGAINST_GOLD' THEN 'LOAN_AGAINST_GOLD'
        WHEN 'General' THEN 'PERSONAL_LOAN'
        ELSE t
        END;
END
$fn$;

CREATE OR REPLACE FUNCTION _v32_map_borrower_type(p_raw text) RETURNS text
LANGUAGE plpgsql
IMMUTABLE
AS
$fb$
DECLARE
    t text;
BEGIN
    t := BTRIM(p_raw);
    IF t = '' THEN
        RETURN p_raw;
    END IF;
    t := UPPER(t);
    RETURN CASE t
        WHEN 'INDIVIDUAL' THEN 'INDIVIDUAL'
        WHEN 'PROPRIETOR' THEN 'PROPRIETOR'
        WHEN 'PARTNERSHIP' THEN 'PARTNERSHIP'
        WHEN 'COMPANY' THEN 'COMPANY'
        ELSE t
        END;
END
$fb$;

-- ─── Apply mapping ─────────────────────────────────────────────────────
UPDATE loan_applications
SET loan_product = _v32_map_loan_product(loan_product),
    borrower_type  = _v32_map_borrower_type(borrower_type)
WHERE _v32_map_loan_product(loan_product) IS DISTINCT FROM loan_product
   OR _v32_map_borrower_type(borrower_type) IS DISTINCT FROM borrower_type;

UPDATE workflow_configs
SET loan_product  = _v32_map_loan_product(loan_product),
    borrower_type = _v32_map_borrower_type(borrower_type)
WHERE _v32_map_loan_product(loan_product) IS DISTINCT FROM loan_product
   OR _v32_map_borrower_type(borrower_type) IS DISTINCT FROM borrower_type;

UPDATE underwriting_rule_sets
SET loan_product  = _v32_map_loan_product(loan_product),
    borrower_type = _v32_map_borrower_type(borrower_type)
WHERE _v32_map_loan_product(loan_product) IS DISTINCT FROM loan_product
   OR _v32_map_borrower_type(borrower_type) IS DISTINCT FROM borrower_type;

UPDATE underwriting_scorecards
SET loan_product  = _v32_map_loan_product(loan_product),
    borrower_type = _v32_map_borrower_type(borrower_type)
WHERE _v32_map_loan_product(loan_product) IS DISTINCT FROM loan_product
   OR _v32_map_borrower_type(borrower_type) IS DISTINCT FROM borrower_type;

UPDATE assignment_rule_sets
SET loan_product  = _v32_map_loan_product(loan_product),
    borrower_type = _v32_map_borrower_type(borrower_type)
WHERE _v32_map_loan_product(loan_product) IS DISTINCT FROM loan_product
   OR _v32_map_borrower_type(borrower_type) IS DISTINCT FROM borrower_type;

UPDATE user_role_mappings
SET loan_product  = _v32_map_loan_product(loan_product)
WHERE loan_product IS NOT NULL
  AND _v32_map_loan_product(loan_product) IS DISTINCT FROM loan_product;

-- Also normalize borrower in role mappings
UPDATE user_role_mappings
SET borrower_type = _v32_map_borrower_type(borrower_type)
WHERE borrower_type IS NOT NULL
  AND _v32_map_borrower_type(borrower_type) IS DISTINCT FROM borrower_type;

-- ─── Deduplicate workflow configs: at most one row per (borrower_type, loan_product) ─
UPDATE workflow_configs w
SET active     = false,
    updated_at = now()
    FROM (SELECT id
          FROM (SELECT id,
                       ROW_NUMBER() OVER (
                           PARTITION BY borrower_type, loan_product
                           ORDER BY version DESC, COALESCE(updated_at, created_at) DESC
                           ) AS rn
                FROM workflow_configs) x
          WHERE x.rn > 1) d
WHERE w.id = d.id;

-- ─── Default workflow step JSON (PERFIOS) ──────────────────────────────
-- MOBILE_OTP, AADHAAR_OTP, PAN_VERIFY, BANK_PENNY_DROP — order 1–4

-- Upsert: ensure one active default workflow for each of 32 segments
DO
$$
    DECLARE
        v_steps  jsonb := '[
          {"step":"MOBILE_OTP","mandatory":true,"order":1,"provider":"PERFIOS"},
          {"step":"AADHAAR_OTP","mandatory":true,"order":2,"provider":"PERFIOS"},
          {"step":"PAN_VERIFY","mandatory":true,"order":3,"provider":"PERFIOS"},
          {"step":"BANK_PENNY_DROP","mandatory":true,"order":4,"provider":"PERFIOS"}
        ]'::jsonb;
        v_bt     text;
        v_lp     text;
        v_name   text;
        v_bt_d   text;
        v_lp_d   text;
        v_idx    int := 0;
        v_br     record;
        v_pr     record;
    BEGIN
        FOR v_br IN
            SELECT * FROM (VALUES
                ('INDIVIDUAL', 'Individual'),
                ('PROPRIETOR', 'Proprietor'),
                ('PARTNERSHIP', 'Partnership'),
                ('COMPANY', 'Company')
                            ) AS t(bt, btd)
            LOOP
                v_bt := v_br.bt;
                v_bt_d := v_br.btd;
                FOR v_pr IN
                    SELECT * FROM (VALUES
                        ('PERSONAL_LOAN', 'Personal Loan'),
                        ('BUSINESS_TERM_LOAN', 'Business Term Loan'),
                        ('BUSINESS_WC_OD', 'Business Working Capital Loan -- Overdraft'),
                        ('BUSINESS_WC_INVOICE_DISCOUNTING', 'Business Working Capital Loan -- Invoice Discounting'),
                        ('TERM_LOAN', 'Term Loan'),
                        ('LOAN_AGAINST_PROPERTY', 'Loan Against Property'),
                        ('LOAN_AGAINST_SECURITIES', 'Loan Against Securities'),
                        ('LOAN_AGAINST_GOLD', 'Loan Against Gold')
                        ) AS p(lp, lpd)
                    LOOP
                        v_lp := v_pr.lp;
                        v_lp_d := v_pr.lpd;
                        v_idx := v_idx + 1;
                        v_name := 'Default Workflow - ' || v_bt_d || ' - ' || v_lp_d;
                        IF NOT EXISTS(SELECT 1
                                      FROM workflow_configs c
                                      WHERE c.borrower_type = v_bt
                                        AND c.loan_product = v_lp
                                        AND c.active) THEN
                            INSERT INTO workflow_configs (id, name, borrower_type, loan_product, steps, active, version, created_at, updated_at)
                            VALUES (('f3330000-0000-4000-a000-0000' || LPAD(v_idx::text, 8, '0'))::uuid,
                                    v_name, v_bt, v_lp, v_steps, true, 1, now(), now());
                        ELSE
                            UPDATE workflow_configs
                            SET name       = v_name,
                                steps    = v_steps,
                                version  = version + 1,
                                active   = true,
                                updated_at = now()
                            WHERE id = (SELECT id
                                        FROM workflow_configs c
                                        WHERE c.borrower_type = v_bt
                                          AND c.loan_product = v_lp
                                        ORDER BY version DESC, COALESCE(c.updated_at, c.created_at) DESC
                            LIMIT 1);
                        END IF;
                    END LOOP;
            END LOOP;
    END
$$;

-- ─── 32 default underwriting rule sets (priority 100) ────────────────────
DO
$$
    DECLARE
        v_bt   text;
        v_lp   text;
        v_j    jsonb := '{"minBureauScore":650,"maxLoanAmount":50000000,"requireKycSuccess":true,"decision":"MANUAL_REVIEW","reasons":["Default underwriting policy"]}'::jsonb;
        v_idx  int  := 0;
        v_name text;
        v_btr  record;
        v_prr  record;
    BEGIN
        FOR v_btr IN
            SELECT * FROM (VALUES
                ('INDIVIDUAL'),
                ('PROPRIETOR'),
                ('PARTNERSHIP'),
                ('COMPANY')
                            ) AS b(code)
            LOOP
                v_bt := v_btr.code;
                FOR v_prr IN
                    SELECT * FROM (VALUES
                        ('PERSONAL_LOAN'),
                        ('BUSINESS_TERM_LOAN'),
                        ('BUSINESS_WC_OD'),
                        ('BUSINESS_WC_INVOICE_DISCOUNTING'),
                        ('TERM_LOAN'),
                        ('LOAN_AGAINST_PROPERTY'),
                        ('LOAN_AGAINST_SECURITIES'),
                        ('LOAN_AGAINST_GOLD')
                        ) AS p(code)
                    LOOP
                        v_lp := v_prr.code;
                        v_idx := v_idx + 1;
                        v_name := 'Default policy — ' || v_bt || ' — ' || v_lp;
                        IF NOT EXISTS(SELECT 1
                                      FROM underwriting_rule_sets u
                                      WHERE u.borrower_type = v_bt
                                        AND u.loan_product = v_lp
                                        AND u.active
                                        AND u.name LIKE 'Default policy%') THEN
                            INSERT INTO underwriting_rule_sets (id, name, borrower_type, loan_product, min_amount, max_amount,
                                                                geography, min_tenure_months, max_tenure_months, priority, active, rules_json, created_at, updated_at)
                            VALUES (('c3320000-0000-4000-a000-0000' || LPAD(v_idx::text, 8, '0'))::uuid,
                                    v_name, v_bt, v_lp, 50000, 50000000, NULL, NULL, 60, 100, true, v_j, now(), now());
                        END IF;
                    END LOOP;
            END LOOP;
    END
$$;

-- ─── 32 default scorecards (priority 100) — same structure as Individual Personal in V26 ─
DO
$$
    DECLARE
        v_bt   text;
        v_lp   text;
        v_idx  int  := 0;
        v_name text;
        v_sj   jsonb;
        v_tj   jsonb;
        v_hj   jsonb;
        v_sbt  record;
        v_slp  record;
    BEGIN
        v_sj := $pl$
        {"rows":[
        {"id":"d1","parameter":"BUREAU_SCORE","source":"BUREAU","condition":"GTE:750","weight":1,"score":35,"attachment":"BUREAU_REPORT"},
        {"id":"d2","parameter":"BUREAU_SCORE","source":"BUREAU","condition":"GTE:650","weight":1,"score":25,"attachment":"BUREAU_REPORT"},
        {"id":"d3","parameter":"MONTHLY_INCOME","source":"MANUAL_OR_PROVIDER","condition":"GTE:50000","weight":1,"score":25,"attachment":"INCOME_PROOF"},
        {"id":"d4","parameter":"MONTHLY_INCOME","source":"MANUAL_OR_PROVIDER","condition":"GTE:25000","weight":1,"score":15,"attachment":"INCOME_PROOF"},
        {"id":"d5","parameter":"OBLIGATION_RATIO","source":"SYSTEM","condition":"LTE:40","weight":1,"score":20,"attachment":"BANK_STATEMENT"},
        {"id":"d6","parameter":"OBLIGATION_RATIO","source":"SYSTEM","condition":"LTE:60","weight":1,"score":10,"attachment":"BANK_STATEMENT"},
        {"id":"d7","parameter":"AVERAGE_BANK_BALANCE","source":"BANK_STATEMENT","condition":"GTE:20000","weight":1,"score":10,"attachment":"BANK_STATEMENT"},
        {"id":"d8","parameter":"KYC_QUALITY","source":"SYSTEM","condition":"EQ:PASS","weight":1,"score":10,"attachment":"KYC_DOCUMENTS"}]}
        $pl$::jsonb;
        v_tj := $thr$
        {"approveMinPercent":70,"manualMinPercent":50,"rejectBelowPercent":50}
        $thr$::jsonb;
        v_hj := $hrb$
        {"rules":[{"id":"h1","parameter":"KYC_QUALITY","source":"SYSTEM","condition":"NE:PASS","decision":"REJECT","message":"KYC not completed successfully"},{"id":"h2","parameter":"BUREAU_SCORE","source":"BUREAU","condition":"LT:600","decision":"REJECT","message":"Bureau score below policy threshold"}]}
        $hrb$::jsonb;

        FOR v_sbt IN
            SELECT * FROM (VALUES
                ('INDIVIDUAL'),
                ('PROPRIETOR'),
                ('PARTNERSHIP'),
                ('COMPANY')
                        ) AS sb(code)
            LOOP
                v_bt := v_sbt.code;
                FOR v_slp IN
                    SELECT * FROM (VALUES
                        ('PERSONAL_LOAN'),
                        ('BUSINESS_TERM_LOAN'),
                        ('BUSINESS_WC_OD'),
                        ('BUSINESS_WC_INVOICE_DISCOUNTING'),
                        ('TERM_LOAN'),
                        ('LOAN_AGAINST_PROPERTY'),
                        ('LOAN_AGAINST_SECURITIES'),
                        ('LOAN_AGAINST_GOLD')
                        ) AS sp(code)
                    LOOP
                        v_lp := v_slp.code;
                        v_idx := v_idx + 1;
                        v_name := 'Default scorecard — ' || v_bt || ' — ' || v_lp;
                        IF NOT EXISTS(SELECT 1
                                      FROM underwriting_scorecards s
                                      WHERE s.borrower_type = v_bt
                                        AND s.loan_product = v_lp
                                        AND s.active
                                        AND s.name LIKE 'Default scorecard%') THEN
                            INSERT INTO underwriting_scorecards (id, name, borrower_type, loan_product, version, priority, min_amount, max_amount, geography, scorecard_json, thresholds_json, hard_rules_json, active, created_at, updated_at)
                            VALUES (('d3320000-0000-4000-a000-0000' || LPAD(v_idx::text, 8, '0'))::uuid,
                                    v_name, v_bt, v_lp, 1, 100, 50000, 50000000, NULL, v_sj, v_tj, v_hj, true, now(), now());
                        END IF;
                    END LOOP;
            END LOOP;
    END
$$;

-- Deactivate any extra *active* rows for the same (borrower_type, loan_product) so a unique
-- partial index can be created. The DO block above only updates one id in the ELSE branch; if
-- multiple actives were present (e.g. legacy + seed), the duplicate key error would follow.
UPDATE workflow_configs w
SET active     = false,
    updated_at = now()
FROM (SELECT id
      FROM (SELECT id,
                   ROW_NUMBER() OVER (
                       PARTITION BY borrower_type, loan_product
                       ORDER BY version DESC, COALESCE(updated_at, created_at) DESC, id
                       ) AS rn
            FROM workflow_configs
            WHERE active = true) x
      WHERE x.rn > 1) d
WHERE w.id = d.id;

-- Partial unique: one *active* workflow per borrower × product
CREATE UNIQUE INDEX IF NOT EXISTS uq_workflow_cfg_active_borrower_product
    ON workflow_configs (borrower_type, loan_product)
    WHERE active = true;

DROP FUNCTION IF EXISTS _v32_map_loan_product(text);
DROP FUNCTION IF EXISTS _v32_map_borrower_type(text);

-- Bureau GACAT → production underwriting bridge for PERSONAL_LOAN.
-- Adds LIVE_UNSECURED_LOAN_COUNT / MAX_DPD_6M to active Individual Personal Loan scorecards
-- (legacy keys fed by CanonicalBureauContextBridge from GACAT metrics).
-- Ensures PERSONAL_LOAN workflow has BUREAU_PULL + auto-pull after KYC.

WITH target AS (
    SELECT id
    FROM underwriting_scorecards
    WHERE active = TRUE
      AND borrower_type = 'INDIVIDUAL'
      AND loan_product IN ('PERSONAL_LOAN', 'Personal Loan', 'Personal')
),
patched AS (
    SELECT
        s.id,
        CASE
            WHEN EXISTS (
                SELECT 1
                FROM jsonb_array_elements(COALESCE(s.scorecard_json -> 'rows', '[]'::jsonb)) e
                WHERE e ->> 'parameter' = 'LIVE_UNSECURED_LOAN_COUNT'
            ) THEN s.scorecard_json
            ELSE jsonb_set(
                COALESCE(s.scorecard_json, '{}'::jsonb),
                '{rows}',
                COALESCE(s.scorecard_json -> 'rows', '[]'::jsonb) || '[
                  {"id":"gacat_live_unsec","parameter":"LIVE_UNSECURED_LOAN_COUNT","source":"BUREAU","condition":"LTE:6","weight":4,"score":4,"attachment":"BUREAU_REPORT"},
                  {"id":"gacat_max_dpd_6m","parameter":"MAX_DPD_6M","source":"BUREAU","condition":"LTE:30","weight":4,"score":4,"attachment":"BUREAU_REPORT"}
                ]'::jsonb,
                TRUE
            )
        END AS scorecard_json,
        CASE
            WHEN EXISTS (
                SELECT 1
                FROM jsonb_array_elements(COALESCE(s.hard_rules_json -> 'rules', '[]'::jsonb)) e
                WHERE e ->> 'parameter' = 'LIVE_UNSECURED_LOAN_COUNT'
            ) THEN s.hard_rules_json
            ELSE jsonb_set(
                COALESCE(s.hard_rules_json, '{}'::jsonb),
                '{rules}',
                COALESCE(s.hard_rules_json -> 'rules', '[]'::jsonb) || '[
                  {"id":"gacat_hard_unsec","parameter":"LIVE_UNSECURED_LOAN_COUNT","source":"BUREAU","condition":"GT:6","decision":"REJECT","message":"Live unsecured loan count exceeds policy (max 6)"},
                  {"id":"gacat_hard_dpd6","parameter":"MAX_DPD_6M","source":"BUREAU","condition":"GT:90","decision":"MANUAL_REVIEW","message":"Max DPD in last 6 months exceeds soft review threshold"}
                ]'::jsonb,
                TRUE
            )
        END AS hard_rules_json
    FROM underwriting_scorecards s
    JOIN target t ON t.id = s.id
)
UPDATE underwriting_scorecards s
SET scorecard_json = p.scorecard_json,
    hard_rules_json = p.hard_rules_json,
    updated_at = now()
FROM patched p
WHERE s.id = p.id;

UPDATE workflow_configs
SET steps = CASE
                WHEN steps::text LIKE '%BUREAU_PULL%' THEN steps
                ELSE COALESCE(steps, '[]'::jsonb) || '[{"step":"BUREAU_PULL","mandatory":true,"order":20,"provider":"EQUIFAX"}]'::jsonb
            END,
    auto_pull_bureau_after_kyc_success = TRUE,
    bureau_enabled = TRUE,
    updated_at = now()
WHERE active = TRUE
  AND borrower_type = 'INDIVIDUAL'
  AND loan_product IN ('PERSONAL_LOAN', 'Personal Loan', 'Personal');

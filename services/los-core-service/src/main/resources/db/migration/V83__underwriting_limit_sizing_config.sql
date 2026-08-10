-- Seed configurable limitSizing on SCF underwriting rule sets (amounts + cap toggles).
-- Policy outcomes (reject / manual review) remain on hardRules — not hardcoded in application flow.

UPDATE underwriting_rule_sets
SET rules_json = jsonb_set(
        COALESCE(rules_json, '{}'::jsonb),
        '{limitSizing}',
        '{"enabled":true,"turnoverParameter":"ANNUAL_GST_TURNOVER","turnoverLimitPercent":0.25,"standardTicketCap":5000000,"maxDeviationCap":10000000,"sanctionCapEnabled":true,"camRecommendedCapEnabled":true}'::jsonb,
        true),
    updated_at = NOW()
WHERE loan_product = 'BUSINESS_WC_INVOICE_DISCOUNTING'
  AND name LIKE 'SCF Invoice Discounting policy%'
  AND (rules_json->'limitSizing' IS NULL OR rules_json->'limitSizing' = 'null'::jsonb);

-- Make the legacy proprietor SCF limit-sizing behavior explicit without replacing its other settings.
UPDATE underwriting_rule_sets
SET rules_json = jsonb_set(
        rules_json,
        '{limitSizing,standardCapMode}',
        '"MIN_OF_BOTH"'::jsonb,
        true),
    updated_at = NOW()
WHERE name = 'SCF Invoice Discounting policy — PROPRIETOR'
  AND rules_json->'limitSizing' IS NOT NULL
  AND jsonb_typeof(rules_json->'limitSizing') = 'object';

-- Round B item 12 — upsert collateral.ltv so DESCRIBE / concept resolve can bind LTV.
-- Authoring overlay only; production_ready remains false.

DO $$
DECLARE
  pid UUID;
  vid UUID;
BEGIN
  SELECT id INTO pid FROM ci_gacat_canonical_parameter WHERE canonical_id = 'collateral.ltv';
  IF pid IS NULL THEN
    pid := gen_random_uuid();
    INSERT INTO ci_gacat_canonical_parameter (
      id, canonical_id, display_name, description, source_family, parameter_kind,
      value_type, unit, period, availability, status,
      source_available, normalized, derivation_defined, implemented, production_ready,
      manual_input_allowed, active, live_rule_parameter, live_scorecard_parameter,
      capability_schema, cardinality, created_at, updated_at
    ) VALUES (
      pid,
      'collateral.ltv',
      'LTV',
      'Loan amount divided by eligible collateral value, expressed as a percent',
      'COMPUTED',
      'DERIVED',
      'PERCENT',
      'PERCENT',
      'PIT',
      'DERIVED',
      'ACTIVE',
      TRUE, TRUE, TRUE, TRUE, FALSE,
      FALSE, TRUE, 'LTV', 'LTV',
      'COMPUTED', 'SINGLE', NOW(), NOW()
    );
  ELSE
    UPDATE ci_gacat_canonical_parameter
    SET display_name = 'LTV',
        description = 'Loan amount divided by eligible collateral value, expressed as a percent',
        source_available = TRUE,
        normalized = TRUE,
        derivation_defined = TRUE,
        implemented = TRUE,
        production_ready = FALSE,
        updated_at = NOW()
    WHERE id = pid;
  END IF;

  SELECT id INTO vid
  FROM ci_gacat_canonical_parameter_version
  WHERE parameter_id = pid AND version_no = 1;

  IF vid IS NULL THEN
    INSERT INTO ci_gacat_canonical_parameter_version (
      id, parameter_id, version_no, effective_from, definition_status,
      calculation_definition, missing_data_treatment, period_definition,
      implementation_binding, created_at, created_by
    ) VALUES (
      gen_random_uuid(),
      pid,
      1,
      NOW(),
      'ACTIVE',
      'Loan amount / eligible collateral value * 100',
      'DATA_INSUFFICIENT',
      'PIT',
      'Collateral valuation / LTV ratio',
      NOW(),
      'V123'
    );
  END IF;

  INSERT INTO ci_gacat_canonical_parameter_alias (id, parameter_id, alias, alias_type, active)
  SELECT gen_random_uuid(), pid, a.alias, 'SYNONYM', TRUE
  FROM (VALUES
    ('ltv'),
    ('loan to value'),
    ('loan-to-value'),
    ('loan to value ratio')
  ) AS a(alias)
  WHERE NOT EXISTS (
    SELECT 1 FROM ci_gacat_canonical_parameter_alias x
    WHERE x.parameter_id = pid AND LOWER(x.alias) = LOWER(a.alias)
  );
END $$;

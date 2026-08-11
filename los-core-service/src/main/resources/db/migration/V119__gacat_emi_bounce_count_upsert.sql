-- POLICY-DATA-CALC-FUNCTIONAL-COMPLETION-1
-- Upsert canonical banking.emi_bounce_count_3m with EmiBounceCountCalculator.V1 binding.
-- Does not mutate policy-scoped configuration; does not enable production_ready / live authority.

DO $$
DECLARE
  pid UUID;
  vid UUID;
BEGIN
  SELECT id INTO pid FROM ci_gacat_canonical_parameter WHERE canonical_id = 'banking.emi_bounce_count_3m';
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
      'banking.emi_bounce_count_3m',
      'EMI bounce count',
      'Count of EMI repayment events with matched return/bounce events in trailing 3 months '
        || '(existing EMI + bounce/return classifiers; DATA_INSUFFICIENT when coverage missing)',
      'BANK_STATEMENT',
      'DERIVED',
      'COUNT',
      'COUNT',
      'TRAILING_3M',
      'DERIVED',
      'ACTIVE',
      TRUE, TRUE, TRUE, TRUE, FALSE,
      FALSE, TRUE, NULL, NULL,
      'BANK_STATEMENT', 'SINGLE', NOW(), NOW()
    );
  ELSE
    UPDATE ci_gacat_canonical_parameter
    SET display_name = 'EMI bounce count',
        description = 'Count of EMI repayment events with matched return/bounce events in trailing 3 months '
          || '(existing EMI + bounce/return classifiers; DATA_INSUFFICIENT when coverage missing)',
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
      'Count EMI repayment events with matched return/bounce events during the period',
      'DATA_INSUFFICIENT',
      'TRAILING_3M',
      'EmiBounceCountCalculator.V1',
      NOW(),
      'V119'
    );
  ELSE
    UPDATE ci_gacat_canonical_parameter_version
    SET calculation_definition = 'Count EMI repayment events with matched return/bounce events during the period',
        missing_data_treatment = 'DATA_INSUFFICIENT',
        period_definition = 'TRAILING_3M',
        implementation_binding = 'EmiBounceCountCalculator.V1',
        definition_status = 'ACTIVE'
    WHERE id = vid;
  END IF;

  -- Aliases (ignore duplicates)
  INSERT INTO ci_gacat_canonical_parameter_alias (id, parameter_id, alias, alias_type, active)
  SELECT gen_random_uuid(), pid, a.alias, 'SYNONYM', TRUE
  FROM (VALUES
    ('emi bounce'),
    ('emi bounce count'),
    ('bounced emi'),
    ('emi return count')
  ) AS a(alias)
  WHERE NOT EXISTS (
    SELECT 1 FROM ci_gacat_canonical_parameter_alias x
    WHERE x.parameter_id = pid AND LOWER(x.alias) = LOWER(a.alias)
  );
END $$;

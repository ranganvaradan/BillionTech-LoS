-- POLICY-STUDIO-GATE3 — GACAT executability honesty sync
-- 1) EMI bounce: runtime BankingMetricService + snapshot path → production_ready=true
-- 2) Insert Policy-Test-only write-off / NTC params if missing (production_ready=false)

DO $$
DECLARE
  pid UUID;
BEGIN
  -- EMI bounce production honesty
  UPDATE ci_gacat_canonical_parameter
  SET implemented = TRUE,
      production_ready = TRUE,
      derivation_defined = TRUE,
      source_available = TRUE,
      normalized = TRUE,
      description = 'Count of EMI repayment events with matched return/bounce events in trailing 3 months '
        || '(BankingMetricService.countEmiBounce / EmiBounceCountCalculator.V1; DATA_INSUFFICIENT when coverage missing)',
      updated_at = NOW()
  WHERE canonical_id = 'banking.emi_bounce_count_3m';

  UPDATE ci_gacat_canonical_parameter_version v
  SET implementation_binding = 'BankingMetricService.countEmiBounce / EmiBounceCountCalculator.V1',
      missing_data_treatment = 'DATA_INSUFFICIENT'
  FROM ci_gacat_canonical_parameter p
  WHERE v.parameter_id = p.id
    AND p.canonical_id = 'banking.emi_bounce_count_3m'
    AND v.definition_status = 'ACTIVE'
    AND v.effective_to IS NULL;

  -- bureau.status_ntc (Policy-Test / studio; Live scorecard uses NTC_FLAG)
  SELECT id INTO pid FROM ci_gacat_canonical_parameter WHERE canonical_id = 'bureau.status_ntc';
  IF pid IS NULL THEN
    pid := gen_random_uuid();
    INSERT INTO ci_gacat_canonical_parameter (
      id, canonical_id, display_name, description, source_family, parameter_kind,
      value_type, unit, period, availability, status,
      source_available, normalized, derivation_defined, implemented, production_ready,
      manual_input_allowed, active, live_rule_parameter, live_scorecard_parameter,
      capability_schema, cardinality, created_at, updated_at
    ) VALUES (
      pid, 'bureau.status_ntc', 'Bureau NTC / thin-file status',
      'True when bureau report is NTC / thin-file (PolicyBureauMetricService.consumerNtc); Live scorecard maps NTC_FLAG',
      'Bureau Retail', 'DERIVED', 'BOOLEAN', 'BOOLEAN', 'PIT', 'DERIVED', 'ACTIVE',
      TRUE, TRUE, TRUE, TRUE, FALSE,
      FALSE, TRUE, NULL, 'NTC_FLAG',
      'BUREAU_RETAIL', 'SCALAR', NOW(), NOW()
    );
    INSERT INTO ci_gacat_canonical_parameter_version (
      id, parameter_id, version_no, effective_from, definition_status,
      calculation_definition, missing_data_treatment, period_definition,
      implementation_binding, created_at, created_by
    ) VALUES (
      gen_random_uuid(), pid, 1, NOW(), 'ACTIVE',
      'PolicyBureauMetricService.consumerNtc / Scorecard NTC_FLAG',
      'DATA_INSUFFICIENT — do not invent false',
      'PIT',
      'PolicyBureauMetricService.consumerNtc',
      NOW(), 'V120_GATE3'
    );
  END IF;

  -- write-off non-CC (Policy-Test only)
  SELECT id INTO pid FROM ci_gacat_canonical_parameter WHERE canonical_id = 'bureau.accounts.writeoff_non_cc';
  IF pid IS NULL THEN
    pid := gen_random_uuid();
    INSERT INTO ci_gacat_canonical_parameter (
      id, canonical_id, display_name, description, source_family, parameter_kind,
      value_type, unit, period, availability, status,
      source_available, normalized, derivation_defined, implemented, production_ready,
      manual_input_allowed, active, live_rule_parameter, live_scorecard_parameter,
      capability_schema, cardinality, created_at, updated_at
    ) VALUES (
      pid, 'bureau.accounts.writeoff_non_cc', 'Non-credit-card write-off count',
      'Count of written-off tradelines excluding credit cards (PolicyBureauMetricService.writeoffCounts) — Policy Test only',
      'Bureau', 'DERIVED', 'COUNT', 'COUNT', 'PIT', 'DERIVED', 'ACTIVE',
      TRUE, TRUE, TRUE, TRUE, FALSE,
      FALSE, TRUE, NULL, NULL,
      'BUREAU_RETAIL', 'SCALAR', NOW(), NOW()
    );
    INSERT INTO ci_gacat_canonical_parameter_version (
      id, parameter_id, version_no, effective_from, definition_status,
      calculation_definition, missing_data_treatment, period_definition,
      implementation_binding, created_at, created_by
    ) VALUES (
      gen_random_uuid(), pid, 1, NOW(), 'ACTIVE',
      'Count of written-off tradelines excluding credit cards',
      'DATA_INSUFFICIENT',
      'PIT',
      'PolicyBureauMetricService.writeoffCounts',
      NOW(), 'V120_GATE3'
    );
  ELSE
    UPDATE ci_gacat_canonical_parameter
    SET implemented = TRUE, production_ready = FALSE, updated_at = NOW()
    WHERE id = pid;
  END IF;

  -- write-off CC only
  SELECT id INTO pid FROM ci_gacat_canonical_parameter WHERE canonical_id = 'bureau.accounts.cc_writeoff';
  IF pid IS NULL THEN
    pid := gen_random_uuid();
    INSERT INTO ci_gacat_canonical_parameter (
      id, canonical_id, display_name, description, source_family, parameter_kind,
      value_type, unit, period, availability, status,
      source_available, normalized, derivation_defined, implemented, production_ready,
      manual_input_allowed, active, live_rule_parameter, live_scorecard_parameter,
      capability_schema, cardinality, created_at, updated_at
    ) VALUES (
      pid, 'bureau.accounts.cc_writeoff', 'Credit-card write-off count',
      'Count of written-off credit-card tradelines — Policy Test only',
      'Bureau', 'DERIVED', 'COUNT', 'COUNT', 'PIT', 'DERIVED', 'ACTIVE',
      TRUE, TRUE, TRUE, TRUE, FALSE,
      FALSE, TRUE, NULL, NULL,
      'BUREAU_RETAIL', 'SCALAR', NOW(), NOW()
    );
    INSERT INTO ci_gacat_canonical_parameter_version (
      id, parameter_id, version_no, effective_from, definition_status,
      calculation_definition, missing_data_treatment, period_definition,
      implementation_binding, created_at, created_by
    ) VALUES (
      gen_random_uuid(), pid, 1, NOW(), 'ACTIVE',
      'Count of written-off credit-card tradelines',
      'DATA_INSUFFICIENT',
      'PIT',
      'PolicyBureauMetricService.writeoffCounts',
      NOW(), 'V120_GATE3'
    );
  END IF;

  INSERT INTO ci_gacat_catalogue_meta (meta_key, meta_value, updated_at)
  VALUES ('gate3_executability_honesty', 'V120', NOW())
  ON CONFLICT (meta_key) DO UPDATE SET meta_value = EXCLUDED.meta_value, updated_at = EXCLUDED.updated_at;
END $$;

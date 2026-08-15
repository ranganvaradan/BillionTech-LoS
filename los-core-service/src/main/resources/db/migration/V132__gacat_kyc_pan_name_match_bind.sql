-- DP-2B — bind kyc.pan.name_match to existing NormalizedKycFactBuilder emit path.
-- Do not certify productionReady. KYC orchestration remains execution authority.

DO $$
DECLARE
  pid UUID;
  vid UUID;
BEGIN
  SELECT id INTO pid FROM ci_gacat_canonical_parameter WHERE canonical_id = 'kyc.pan.name_match';
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
      'kyc.pan.name_match',
      'PAN name match',
      'Provider name-match boolean when supplied; optional/provider-conditional',
      'KYC',
      'RAW',
      'BOOLEAN',
      'BOOLEAN',
      'SCALAR',
      'AVAILABLE_AUTOMATICALLY',
      'ACTIVE',
      TRUE, TRUE, FALSE, TRUE, FALSE,
      TRUE, TRUE, NULL, NULL,
      'KYC', 'SINGLE', NOW(), NOW()
    );
  ELSE
    UPDATE ci_gacat_canonical_parameter
    SET display_name = 'PAN name match',
        description = 'Provider name-match boolean when supplied; optional/provider-conditional',
        source_available = TRUE,
        normalized = TRUE,
        derivation_defined = FALSE,
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
      'Emit kyc.pan.name_match only when provider nameMatch hint is present',
      'ABSENT_WHEN_UNKNOWN',
      'SCALAR',
      'NormalizedKycFactBuilder',
      NOW(),
      'V132'
    );
  ELSE
    UPDATE ci_gacat_canonical_parameter_version
    SET calculation_definition = 'Emit kyc.pan.name_match only when provider nameMatch hint is present',
        missing_data_treatment = 'ABSENT_WHEN_UNKNOWN',
        implementation_binding = 'NormalizedKycFactBuilder'
    WHERE id = vid;
  END IF;
END $$;

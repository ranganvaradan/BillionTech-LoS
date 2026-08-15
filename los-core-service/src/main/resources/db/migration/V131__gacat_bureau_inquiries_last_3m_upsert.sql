-- DP-2B — restore bureau.inquiries.last_3m DB parity with GacatCatalogueSeed.
-- Semantically distinct from bureau.inquiries.current_month and bureau.recent_inquiries_90d.
-- production_ready remains FALSE (Policy Test / studio calculator — not independently certified).

DO $$
DECLARE
  pid UUID;
  vid UUID;
BEGIN
  SELECT id INTO pid FROM ci_gacat_canonical_parameter WHERE canonical_id = 'bureau.inquiries.last_3m';
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
      'bureau.inquiries.last_3m',
      'Bureau enquiries (last 3 calendar months)',
      'Count of bureau enquiries in the trailing 3 calendar months (distinct from current month and from 90 days)',
      'BUREAU_RETAIL',
      'DERIVED',
      'COUNT',
      'COUNT',
      'TRAILING_3M',
      'DERIVED',
      'ACTIVE',
      TRUE, TRUE, TRUE, TRUE, FALSE,
      FALSE, TRUE, NULL, NULL,
      'BUREAU_RETAIL', 'SINGLE', NOW(), NOW()
    );
  ELSE
    UPDATE ci_gacat_canonical_parameter
    SET display_name = 'Bureau enquiries (last 3 calendar months)',
        description = 'Count of bureau enquiries in the trailing 3 calendar months (distinct from current month and from 90 days)',
        source_available = TRUE,
        normalized = TRUE,
        derivation_defined = TRUE,
        implemented = TRUE,
        production_ready = FALSE,
        period = 'TRAILING_3M',
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
      'Count of bureau enquiries whose date falls in the trailing 3 calendar months (PolicyBureauMetricService.inquiriesLast3Months)',
      'DATA_INSUFFICIENT',
      'TRAILING_3M — calendar months; NOT current_month; NOT trailing 90 days',
      'PolicyBureauMetricService.inquiriesLast3Months',
      NOW(),
      'V131'
    );
  ELSE
    UPDATE ci_gacat_canonical_parameter_version
    SET calculation_definition = 'Count of bureau enquiries whose date falls in the trailing 3 calendar months (PolicyBureauMetricService.inquiriesLast3Months)',
        missing_data_treatment = 'DATA_INSUFFICIENT',
        period_definition = 'TRAILING_3M — calendar months; NOT current_month; NOT trailing 90 days',
        implementation_binding = 'PolicyBureauMetricService.inquiriesLast3Months'
    WHERE id = vid;
  END IF;

  INSERT INTO ci_gacat_canonical_parameter_alias (id, parameter_id, alias, alias_type, active)
  SELECT gen_random_uuid(), pid, a.alias, 'SYNONYM', TRUE
  FROM (VALUES
    ('enquiries last 3 months'),
    ('inquiries 3 months')
  ) AS a(alias)
  WHERE NOT EXISTS (
    SELECT 1 FROM ci_gacat_canonical_parameter_alias x
    WHERE x.parameter_id = pid AND LOWER(x.alias) = LOWER(a.alias)
  );
END $$;

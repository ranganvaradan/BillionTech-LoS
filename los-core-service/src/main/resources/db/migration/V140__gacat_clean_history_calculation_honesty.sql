-- POLICY-GRAPH-AND-DERIVED-PARAMETER-CLOSURE-1
-- Clean history months: raw primitives exist; executable derivation is not certified
-- (vocabulary-gated stub). Do not claim implemented=true / AVAILABLE_AUTOMATICALLY.

UPDATE ci_gacat_canonical_parameter
SET implemented = FALSE,
    production_ready = FALSE,
    availability = 'DERIVABLE_FROM_AVAILABLE_DATA',
    updated_at = NOW()
WHERE canonical_id = 'bureau.credit_after_overdue.clean_history_months';

UPDATE ci_gacat_canonical_parameter_version v
SET missing_data_treatment = 'NEEDS_CONFIGURATION until CM vocabulary resolved; CALCULATION_NOT_IMPLEMENTED',
    calculation_definition = COALESCE(v.calculation_definition, '')
WHERE v.definition_status = 'ACTIVE'
  AND v.effective_to IS NULL
  AND v.parameter_id = (
      SELECT id FROM ci_gacat_canonical_parameter
      WHERE canonical_id = 'bureau.credit_after_overdue.clean_history_months'
  );

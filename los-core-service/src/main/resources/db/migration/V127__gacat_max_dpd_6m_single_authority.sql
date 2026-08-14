-- BUREAU-CERTIFICATION-P0-DPD-SINGLE-AUTHORITY
-- Converge bureau.max_dpd_6m GACAT metadata onto shared BureauMetricService.evaluateMaxDpd.
-- Does NOT set production_ready=true (not production-certified). Does not alter V126.

DO $$
DECLARE
  pid UUID;
  calc_text TEXT :=
    'Highest days-past-due across tradeline payment-history months in a trailing 6 calendar-month window. '
    || 'Period representation: YearMonth (Equifax History48Months month key → first-of-month LocalDate → YearMonth). '
    || 'Window inclusive [YearMonth(asOf) − 5, YearMonth(asOf)]; as-of month included; future periods after asOf month excluded. '
    || 'Null/malformed periods skipped; null DPD skipped (not zero). Aggregation: MAX. '
    || 'No payment-history rows → DATA_INSUFFICIENT (not zero). '
    || 'Calculator: BureauMetricService.evaluateMaxDpd (shared Policy Test + live).';
BEGIN
  SELECT id INTO pid FROM ci_gacat_canonical_parameter WHERE canonical_id = 'bureau.max_dpd_6m';
  IF pid IS NULL THEN
    RAISE NOTICE 'bureau.max_dpd_6m not found — skip V127 honesty update';
    RETURN;
  END IF;

  UPDATE ci_gacat_canonical_parameter
  SET description = calc_text,
      implemented = TRUE,
      production_ready = FALSE,
      derivation_defined = TRUE,
      source_available = TRUE,
      normalized = TRUE,
      updated_at = NOW()
  WHERE id = pid;

  UPDATE ci_gacat_canonical_parameter_version v
  SET calculation_definition = calc_text,
      missing_data_treatment = 'DATA_INSUFFICIENT when no payment history on any tradeline',
      period_definition = 'TRAILING_6M',
      implementation_binding = 'BureauMetricService.evaluateMaxDpd',
      filters_eligibility = 'Tradelines with payment history; YearMonth trailing window shared with live',
      transformation = 'Month DPD numeric value as normalized YearMonth period',
      aggregation = 'MAX',
      definition_status = 'ACTIVE'
  FROM ci_gacat_canonical_parameter p
  WHERE v.parameter_id = p.id
    AND p.id = pid
    AND v.definition_status = 'ACTIVE'
    AND v.effective_to IS NULL;
END $$;

-- Round B item 73 — bind "maximum DPD in last 12 months" to bureau.max_dpd_12m.
-- Narrow greedy 6m aliases that previously stole 12-month phrases.

DO $$
DECLARE
  pid6 UUID;
  pid12 UUID;
BEGIN
  SELECT id INTO pid6 FROM ci_gacat_canonical_parameter WHERE canonical_id = 'bureau.max_dpd_6m';
  SELECT id INTO pid12 FROM ci_gacat_canonical_parameter WHERE canonical_id = 'bureau.max_dpd_12m';

  IF pid6 IS NOT NULL THEN
    DELETE FROM ci_gacat_canonical_parameter_alias
    WHERE parameter_id = pid6
      AND LOWER(alias) IN ('max dpd', 'maximum dpd', 'days past due');
    INSERT INTO ci_gacat_canonical_parameter_alias (id, parameter_id, alias, alias_type, active)
    SELECT gen_random_uuid(), pid6, a.alias, 'SYNONYM', TRUE
    FROM (VALUES
      ('dpd last 6 months'),
      ('maximum dpd 6 months'),
      ('max dpd 6m'),
      ('maximum dpd in last 6 months')
    ) AS a(alias)
    WHERE NOT EXISTS (
      SELECT 1 FROM ci_gacat_canonical_parameter_alias x
      WHERE x.parameter_id = pid6 AND LOWER(x.alias) = LOWER(a.alias)
    );
  END IF;

  IF pid12 IS NOT NULL THEN
    INSERT INTO ci_gacat_canonical_parameter_alias (id, parameter_id, alias, alias_type, active)
    SELECT gen_random_uuid(), pid12, a.alias, 'SYNONYM', TRUE
    FROM (VALUES
      ('max dpd 12m'),
      ('dpd 12 months'),
      ('maximum dpd in last 12 months'),
      ('maximum dpd 12 months'),
      ('max dpd'),
      ('days past due'),
      ('maximum dpd')
    ) AS a(alias)
    WHERE NOT EXISTS (
      SELECT 1 FROM ci_gacat_canonical_parameter_alias x
      WHERE x.parameter_id = pid12 AND LOWER(x.alias) = LOWER(a.alias)
    );
  END IF;
END $$;

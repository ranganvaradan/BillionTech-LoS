# 12 — GST Shadow Comparison

## Shadow rules (`CanonicalGstRuleEvaluator`)

| Rule ID | Behaviour |
|---------|-----------|
| `DQ_GST_AVAILABLE` | Registration present → PASS; else DATA_INSUFFICIENT |
| `HARD_GST_REGISTRATION_STATUS` | ACTIVE → PASS; INACTIVE/CANCELLED/SUSPENDED → FAIL; missing/unknown → DATA_INSUFFICIENT |
| `GST_FILING_TIMELINESS` | `gst.filing.timeliness_score` vs min (default 60) |
| `GST_MISSING_RETURNS` | `gst.return.missing_count_12m` vs max allowed (default 3) |
| `XSRC_GSTR1_GSTR3B_VARIANCE` | MATCH/ACCEPTABLE → PASS; MATERIAL/CONFLICT → FAIL; DI → DATA_INSUFFICIENT |
| `GST_TURNOVER_ELIGIBILITY` | Shadow only: `gst.turnover.trailing_12m` ≥ config threshold (50M). Production still uses CreditControl / SCF 52M gap |

Persisted as `CiStandardRuleResult` with `ruleType=CANONICAL_GST`. Shadow evaluation metadata includes `gstComparison` (mirrors `bureauComparison`).

## Adapter overlay

When `canonicalization.gst.use-for-shadow-rules=true` **and** trailing_12m outcome is `PASS`:

- Overlay scorecard `ANNUAL_GST_TURNOVER` from canonical for **shadow path only**
- Documented in `adapterFlags.gst` (`canonicalValueApplied`, `note`)

When `DATA_INSUFFICIENT`: keep legacy scorecard value and set `fallbackUsed=true`.

## Snapshot facts

- Emit `gst.*` facts from metrics/registration when canonical available
- Compat `ANNUAL_GST_TURNOVER` gets `compatibilitySource=CANONICAL|MANUAL|PROVIDER|DEFAULTED`
- Canonical trailing_12m emitted as `gst.turnover.trailing_12m`
- **Do not** overwrite production scorecard; never set turnover fact to 0 for DATA_INSUFFICIENT

## Mismatch classifications

`GstMismatchClassification`: LEGACY_DEFAULT_USED, LEGACY_MANUAL_VALUE, CANONICAL_DATA_INSUFFICIENT, CANONICAL_TURNOVER_DIFFERENT, MISSING_PERIODS, GSTR1_GSTR3B_VARIANCE, INACTIVE_GSTIN, MULTI_GSTIN_AGGREGATION_DIFFERENCE, DUPLICATE_PERIOD_REMOVED, ANNUALIZATION_DIFFERENCE, PARSER_DIFFERENCE, OTHER

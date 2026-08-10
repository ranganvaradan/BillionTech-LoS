# 45 — Banking BRE Interpretation

Authoritative source: `src/test/resources/policy-fixtures/banking-bre/Banking_BRE.txt` (USER_SUPPLIED_SAMPLE).

## Sections

| Section | Classification |
|---------|----------------|
| Fields Required in Excel Report | `INFORMATION_REQUIREMENT` |
| Banking BRE Rules | `HARD_RULE` / `METRIC_ADJUSTMENT` |

## Product scopes (kept distinct)

| Product | Rules |
|---------|-------|
| STARTER | ADB 3m >= EDI |
| DIGILEAP | ADB/5 >= EDI AND monthly txn >= 20 |
| SMART_SWITCH | Settlement/10 >= EDI AND settlement count >= 20 |
| REBOOST | amount **>** 60000 then ADB/5 >= EDI AND txn >= 30 |

## Metric adjustments → `BANK_POLICY_ADJUSTED_ADB`

- Exclude loan disbursements 3m
- Exclude online gaming credits
- Exclude bulk deposit >10× average depositions (denominator ambiguous)

## Ambiguities

EDI (`UNKNOWN_BUSINESS_TERM`), average monthly txn (`MULTIPLE_CANONICAL_MATCHES`), exactly 100 txn (`BOUNDARY_AMBIGUITY`), average depositions (`UNCLEAR_DENOMINATOR`), settlement (`MISSING_METRIC` when UNAVAILABLE).

## System rule IDs (deterministic)

`BANK_STARTER_ADB_GTE_EDI`, `BANK_DIGILEAP_*`, `BANK_SMART_SWITCH_*`, `BANK_REBOOST_*`, `BANK_INWARD_RETURN_BRANCHED_100`

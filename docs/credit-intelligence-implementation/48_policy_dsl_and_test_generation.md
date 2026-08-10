# 48 — Policy DSL and Test Generation

## DSL operators

AND, OR, NOT, IF, EQ, NE, GT, GTE, LT, LTE, EXISTS, DIVIDE, …

Example:

```json
{
  "ruleId": "BUREAU_DPD_LAST_6M",
  "version": "DRAFT",
  "type": "HARD",
  "expression": {
    "op": "GT",
    "left": { "metric": "bureau.max_dpd_6m" },
    "right": 30
  },
  "onTrue": "FAIL",
  "onFalse": "PASS",
  "onMissing": "DATA_INSUFFICIENT"
}
```

System assigns `systemRuleId` — AI never invents authoritative IDs.

## Golden boundaries

| Rule | Cases |
|------|-------|
| DPD | 29/30 PASS, 31 FAIL, missing DI, 31 @ 8m ago PASS |
| Inquiry | 3 PASS, 4 FAIL (eval clock) |
| CC overdue | 5000 PASS, 5001 FAIL |
| Starter | ADB=EDI PASS |
| DigiLeap txn | 20 PASS, 19 FAIL |
| Reboost | 60000 inactive, 60001 active |

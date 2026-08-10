# 33 — Provider Fixture Contracts

Golden corpus: `los-core-service/src/test/resources/provider-fixtures/`

| Path | Origin |
|------|--------|
| `surepass/bsa/bsa_minimal.json` | USER_SUPPLIED_SAMPLE (trimmed from TESTED_SUREPASS_BSA) |
| `surepass/gst/gst_monthly_minimal.json` | USER_SUPPLIED_SAMPLE |
| `surepass/itr/itr_income_heads_distinct.json` | USER_SUPPLIED_SAMPLE (income heads deliberately distinct) |
| `surepass/cibil/cibil_minimal.json` | USER_SUPPLIED_SAMPLE |
| `surepass/commercial/commercial_minimal.json` | USER_SUPPLIED_SAMPLE |
| `surepass/tis/tis_amounts_distinct.json` | USER_SUPPLIED_SAMPLE |
| `equifax/equifax_accounts_minimal.xml` | SYNTHETIC / test-derived |
| `karza/gst|itr/*` | SYNTHETIC / test-derived |
| `setu/aa_fi_minimal.json` | SYNTHETIC |

Contract: fixture → ProviderAdapter → canonical extract. Parser/normalizer versions recorded. Not live production data.

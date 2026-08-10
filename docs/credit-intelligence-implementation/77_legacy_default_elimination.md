# 77 — Legacy Default Elimination (G0)

Defaults are inventoried, classified, and optionally quarantined for enabled cohorts. Production gap defaults are **not deleted** in G0.

```mermaid
flowchart TD
  Req[Legacy fallback requested] --> Flag{cutover.enabled + quarantine-enabled + cohort?}
  Flag -->|no| Legacy[allowLegacy=true — unchanged]
  Flag -->|yes| Class{classification}
  Class -->|BUSINESS_POLICY_DEFAULT| Approved[Approved auditable default]
  Class -->|DEMO_ONLY| Refer[REFER]
  Class -->|UNSAFE_SILENT_DEFAULT| Canon{canonical replacement?}
  Canon -->|yes| Use[Use canonical value]
  Canon -->|no| DI[DATA_INSUFFICIENT / REFER]
```

## Classifications

| Class | Meaning |
|-------|---------|
| UNSAFE_SILENT_DEFAULT | Missing data filled with underwriting numbers (e.g. GST ₹5.2Cr) |
| BUSINESS_POLICY_DEFAULT | Explicit policy config (e.g. pricing floor) |
| DEMO_ONLY | Demo fallback path |
| DATA_GAP_FALLBACK / TECHNICAL / DEV_ONLY | Other inventory buckets |

## Catalog source

`LegacyDefaultInventory` + CreditControl known keys → `ci_legacy_default_definition` / `LegacyDefaultCatalogService`.

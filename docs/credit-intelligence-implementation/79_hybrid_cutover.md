# 79 — Hybrid Cutover (G0)

Cutover is dimension-by-dimension, not big-bang.

```mermaid
flowchart LR
  subgraph canonical [Canonical candidates]
    SRC[SOURCE]
    FACT[FACT]
    MET[METRIC]
    REC[RECON]
    POL[POLICY]
    SC[SCORECARD]
  end
  subgraph legacy [Remain LEGACY in G0]
    LIM[LIMIT]
    PR[PRICING]
    TEN[TENURE]
    COL[COLLATERAL]
    AUTH[AUTHORITY]
  end
  Cohort[G0_CANDIDATE_COHORT_V1 DIGILEAP] --> canonical
  Cohort --> legacy
```

## Candidate cohort

- Tenant `00000000-0000-0000-0000-000000000001`
- Product `DIGILEAP` (aliases include `SCF_STARTER`)
- Status `VALIDATION` (never `ACTIVE`)
- Metadata label `G0_CANDIDATE_COHORT_V1`

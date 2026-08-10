# 78 — Dual-Run Certification (G0)

For the same application, persist legacy authoritative outcome alongside canonical shadow Policy + Decision recommendation. Production action remains legacy.

```mermaid
sequenceDiagram
  participant App as Application
  participant Dual as CutoverDualRunService
  participant Pol as ShadowPolicyEngine
  participant Dec as ShadowDecisionEngine
  participant DB as ci_cutover_comparison
  App->>Dual: legacy snapshot (authoritative)
  Dual->>Pol: evaluate (shadow)
  Dual->>Dec: recommend (shadow)
  Dual->>DB: comparison + class + root cause
  Note over App: Production sanction/CAM unchanged
```

## Comparison classes

`EXACT_MATCH`, `NON_MATERIAL_DIFFERENCE`, `MATERIAL_*`, `CANONICAL_REFER`, `CANONICAL_DATA_INSUFFICIENT`, `LEGACY_DEFAULT_DEPENDENT`, `CANONICAL_MORE_PERMISSIVE`, `CANONICAL_STRICTER`.

## Honesty

Dual-run statistics are computed **only** from persisted comparison rows (fixture/dev). Empty sample → null percentages, `insufficientSample=true`. Never fabricate match rates.

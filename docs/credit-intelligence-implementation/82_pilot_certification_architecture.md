# 82 — Pilot Certification Architecture (G0.1)

G0.1 certifies whether one narrow cohort may enter **DUAL_RUN** while production authority remains **LEGACY**. CANONICAL authority stays locked (`allow-canonical-authority: false`).

```mermaid
flowchart TD
  Rank[PilotCandidateRanker] --> Select[Selected cohort]
  Select --> Discover[PilotDataDiscoveryService]
  Discover --> Gates[LimitedPilotCertificationService]
  Gates -->|all gates + real/stored min| LPR[LIMITED_PILOT_READY]
  Gates -->|code gates + sample exception| RWE[READY_WITH_EXCEPTIONS]
  Gates -->|any mandatory fail| NR[NOT_READY]
  LPR --> Enable[DualRunEnablementService]
  RWE --> Enable
  Enable --> Dual[DUAL_RUN control]
  Dual --> Legacy[Legacy remains authoritative]
```

## Status honesty

| Status | Meaning |
|--------|---------|
| `NOT_READY` | Mandatory gate failed **or** real/stored sample below minimum without exception |
| `READY_WITH_EXCEPTIONS` | Code gates pass; audited sample-size exception only — **not** LIMITED_PILOT_READY |
| `LIMITED_PILOT_READY` | All §34 gates including `min-real-or-stored-cases` |
| `REVOKED` | Previously granted certification withdrawn |

AI-Underwriter availability **never** affects the certification score.

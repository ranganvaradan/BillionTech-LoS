# 24 — Cross-Source Reconciliation Engine

Phase C5 introduces a **generic, definition-driven** reconciliation engine under `com.los.core.creditintelligence.reconciliation`.

## Architecture

```mermaid
flowchart TB
  subgraph sources [Canonical sources C1-C4]
    B[Bureau metrics]
    G[GST metrics]
    K[Banking metrics]
    T[ITR / AIS / 26AS metrics]
  end

  subgraph engine [Reconciliation Engine]
    D[CiReconciliationDefinition catalogue]
    O[OperandResolver]
    P[PeriodAlignmentService PERIOD_ALIGNMENT_V1]
    V[VarianceCalculator]
    E[ReconciliationEvaluator]
    L[LegacyReconciliationBridge]
    TRI[TurnoverTriangulationService]
    ORCH[ReconciliationOrchestrator]
  end

  subgraph outputs [Outputs]
    R[CiReconciliationResult]
    EV[CiReconciliationEvidence]
    CES[CiCreditEvidenceSummary]
    F[Fact paths RECONCILED]
    S[Shadow rules]
  end

  sources --> O
  D --> ORCH
  ORCH --> O --> E
  ORCH --> L
  ORCH --> TRI
  P --> E
  V --> E
  E --> R
  L --> R
  TRI --> R
  R --> EV
  R --> CES
  R --> F
  R --> S
```

## Design principles

- Shadow-only, flag-gated (`credit-intelligence.reconciliation.*`)
- Consume existing `CiMetricResult` codes — do not recreate GST/ITR/Bank/Bureau metrics
- Missing operands → `DATA_INSUFFICIENT` / `PERIOD_MISMATCH` — never invent values
- Turnover mismatches favor explain → refer → investigate (not hard-fail)
- Evidence strength is **not** a credit/risk score
- Orchestration never throws into the production underwriting path

## Package layout

| Area | Package |
|------|---------|
| Domain / enums | `…reconciliation.domain` |
| Repositories | `…reconciliation.repository` |
| Engine | `…reconciliation.service` |
| Admin API | `…reconciliation.api` |

## Selective re-evaluation

Definitions carry `dependency_metric_codes`. When `changedMetricCodes` is supplied, only dependent reconciliations (plus triangulation when turnover deps change) re-run.

```mermaid
flowchart LR
  GST[new GST pull] --> M[GST metrics]
  M --> GI[XSRC_GST_ITR_TURNOVER]
  M --> GB[XSRC_GST_BANK_TURNOVER]
  M --> TRI[TURNOVER_TRIANGULATION]
  B[Bureau DPD change] -.->|not invalidated| GI
```

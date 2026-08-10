# 30 — Evaluation Context and Purity

## Contract

`CiEvaluationContext` is the sole immutable input contract for canonical shadow / replay when `credit-intelligence.evaluation-context.enabled=true`.

Pinned inputs:

| Field | Role |
|-------|------|
| `factSnapshotId` | Frozen underwriting facts |
| `policyVersionId` | Frozen `policyContent` |
| `configFreezeId` | Frozen CI thresholds |
| `metricResultSetId` | Pinned metric rows |
| `reconciliationResultSetId` | Pinned recon rows |
| `evaluationAsOf` / `clockInstant` / `clockZone` | Deterministic clock |

`contentHash` covers those inputs; `deterministicEvaluationHash` covers stripped evaluation outcomes (`DeterministicEvaluationHasher`).

## Feature flags (default false except tenant.devMode)

| Flag | Meaning |
|------|---------|
| `evaluationContext.enabled` | Require EvaluationContext path for pure eval |
| `frozenPolicyExecution.enabled` | Evaluate from frozen policy, not live DB rules |
| `configFreeze.enabled` | Persist / require config freeze |
| `providerSpi.enabled` | Provider adapter SPI (not production authority) |
| `providerObservations.enabled` | Persist non-authoritative observations |
| `tenant.requireExplicit` | Never silently default tenant |
| `tenant.devMode` | Allow `defaultTenantId` (local/tests; default **true**) |

## What purity means

Replay of the **same** EvaluationContext must yield the same `deterministicEvaluationHash` even if:

- live LoanApplication fields change
- live UnderwritingRuleSet rows change
- runtime YAML thresholds change (new freeze would differ; original freeze is reused)
- new metric rows are appended (pinned set wins)
- wall clock advances (`FixedEvaluationClock` / context clock fields win)

Purity is **not** claimed for live production underwriting / CreditControl.

## Tests

`EvaluationReplayPurityTest`, `TenantResolverTest`, `FixedEvaluationClockTest`.

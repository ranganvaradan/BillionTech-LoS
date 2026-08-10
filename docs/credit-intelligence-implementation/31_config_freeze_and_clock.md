# 31 — Config Freeze and Evaluation Clock

## ConfigFreeze

`ConfigFreezeService.freezeCurrent(tenantId)` snapshots CI thresholds into `ci_config_freeze`:

- schema: `CONFIG_FREEZE_V1`
- config version: `CI_THRESHOLDS_V1`
- content: canonicalization (bureau/gst/banking/tax), reconciliation tolerances, C5.1 flags
- idempotent via `(tenant_id, content_hash)`

Replay **must not** re-read live YAML; it loads the freeze row referenced by EvaluationContext.

## EvaluationClock

| Implementation | Use |
|----------------|-----|
| `SystemEvaluationClock` | Live / default (zone Asia/Kolkata) |
| `FixedEvaluationClock` | Deterministic as-of / replay |

`EvaluationContextFactory` stores `clockInstant` + `clockZone` + `evaluationAsOf` on the context.

## Tests

`ConfigFreezeServiceTest`, `FixedEvaluationClockTest`.

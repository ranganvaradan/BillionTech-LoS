# 36 — Banking Scale and C5.1 Validation

## Banking partition / index plan (V98)

V98 adds hot-path indexes (partition plan remains operational follow-up):

- `idx_ci_bank_txn_app_date` — `(application_id, transaction_date DESC)`
- `idx_ci_bank_txn_tenant_date` — `(tenant_id, transaction_date DESC)`
- `idx_ci_bank_txn_account_date` — `(bank_account_id, transaction_date DESC)`
- `idx_ci_metric_result_snapshot_code` — partial index on `(fact_snapshot_id, metric_code)`

Future: consider range partitioning `ci_bank_transaction` by month/tenant once volume warrants; indexes above are the C5.1 prerequisite.

## Snapshot purity

`UnderwritingFactSnapshotBuilder.buildAndFreeze` does **not** call GST/banking/tax `ensureIngested` (ingestion stays on foundation prepare hooks). Covered by `SnapshotBuilderNoIngestSideEffectTest`.

## C5.1 validation status

| Area | Status |
|------|--------|
| EvaluationContext + hasher | Unit tests (SYNTHETIC outcomes) |
| ConfigFreeze + FixedClock | Unit tests |
| Frozen policy execution | Unit tests |
| Pinned metrics | Unit tests |
| TenantResolver | Unit tests |
| Snapshot no-ingest | Unit tests |
| ITR income semantics | Unit tests |
| Provider SPI fixtures | USER_SUPPLIED_SAMPLE / SYNTHETIC classpath fixtures |

**Not** live multi-provider E2E. Fixtures are not production validation.

## Remaining gaps

- Live STORED_PROVIDER_FIXTURE app spanning Equifax + Karza + AA + ITR (not unit/fixture only)
- Banking table partition rollout (ops) — indexes from V98 are present
- OperandResolver / ReconciliationOrchestrator still use live latest metrics when purity flags off (transitional); full recon purity when sets pinned
- Scorecard frozen-path still skipped when `frozen-policy-execution.enabled` (hard rules from policy_content only)
- Production authority still unchanged; provider SPI not wired into production ingestion

## Next

**C6 may begin** — C5.1 purity unit + fixture suite is green; flags default false so existing shadow paths stay backward compatible.

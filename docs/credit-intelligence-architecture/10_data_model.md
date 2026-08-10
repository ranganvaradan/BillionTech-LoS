# 10 — Data Model (Logical)

## Core entities

| Entity | Keys / notes | Immutability |
|---|---|---|
| source_record | source_id PK, tenant_id, application_id | Append; status updates allowed |
| source_artifact | artifact_id PK, source_id FK | **Immutable** content |
| ingestion_run | run_id PK | Append |
| consent | consent_id PK, purpose, subject | Versioned |
| entity | entity_id PK, type | Mutable attributes; merge audited |
| entity_identifier | (entity_id, id_type, value_hash) | Unique per tenant |
| entity_relationship | from_id, to_id, rel_type | Temporal |
| fact_definition | path PK, version | Versioned catalog |
| underwriting_fact | fact_id PK | Append; supersession link |
| fact_snapshot | snapshot_id PK, hash | **Immutable** |
| metric_definition | (code, version) PK | Versioned |
| metric_result | result_id, snapshot_id, metric_version | Append |
| reconciliation_definition | (code, version) | Versioned |
| reconciliation_result | result_id, snapshot_id | Append |
| rule_definition | (rule_id, version) | Versioned |
| rule_set | set_id | Soft; versions via package |
| policy_package | package_id | Soft |
| policy_version | version_id PK, package_id, checksum | **Immutable** |
| evaluation | evaluation_id PK, snapshot_id, policy_version_id | Append |
| evaluation_stage | (evaluation_id, stage) | Append |
| rule_result | result_id | Append |
| score_result | result_id | Append |
| recommendation | recommendation_id | Immutable per evaluation |
| decision | decision_id | Append |
| override | override_id | Append |
| approval | approval_id | Append |
| ai_analysis_request | request_id | Append |
| ai_output | output_id | Append |
| ai_output_review | review_id | Append |
| monitoring_case | case_id | Mutable status |
| early_warning_signal | signal_id | Append |
| evidence_reference | evidence_id | Append |
| audit_event | event_id | Append |

## Relationships (summary)

```text
application 1—* source_record *—1 source_artifact
application 1—* fact_snapshot *—* underwriting_fact
fact_snapshot 1—* metric_result / reconciliation_result
policy_version 1—* evaluation
evaluation 1—* evaluation_stage 1—* rule_result
evaluation 1—1 recommendation 1—* decision
ai_output *—1 ai_output_review → may create underwriting_fact (AI_ACCEPTED)
```

## Tenant isolation

All primary tables carry `tenant_id` (or lender_id). Indexes: `(tenant_id, application_id, created_at)`, `(snapshot_id)`, `(policy_version_id)`, `(entity_id, id_type)`.

## PII separation

- Raw artifacts in restricted store  
- Identifiers stored hashed/tokenized where required (Aadhaar)  
- Application logs: no full payloads  

See `diagrams/10_data_model_er.mmd`.

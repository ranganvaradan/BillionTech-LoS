# 11 — API and Event Architecture

## APIs (target)

| Area | Methods |
|---|---|
| Sources | POST ingest, GET source/artifact, POST reparse |
| Facts | POST snapshot, GET snapshot, POST accept-ai-fact |
| Metrics | POST calculate, GET results |
| Reconciliation | POST run, GET result |
| Policy | CRUD draft, POST publish, GET version, POST simulate |
| Evaluation | POST evaluate, GET evaluation/stages/results |
| Decision | GET recommendation, POST decide, POST override |
| AI | POST analysis request, POST/GET outputs, accept/reject/edit |
| Monitoring | CRUD cases, GET signals, POST action |

**Idempotency:** `Idempotency-Key` on ingest, evaluate, publish, AI request.  
**Correlation:** `X-Correlation-Id` / `evaluation_id` across hops.

## Events

```text
source.received
source.normalized
fact.snapshot.created
metric.calculated
reconciliation.completed
policy.published
underwriting.evaluation.started
underwriting.evaluation.completed
decision.recommended
decision.approved
override.requested
override.approved
ai.analysis.requested
ai.analysis.completed
ai.output.accepted
monitoring.signal.detected
```

Payloads carry IDs + hashes, not full PII. At-least-once delivery; consumers idempotent by event_id.

## Transitional

Keep `POST /flow/{id}/underwrite` as façade that triggers Source/Fact snapshot + production engines + shadow; emit new events alongside legacy `AuditService` FLOW events.

# 03 — Source Engine

## Purpose

Ingest, store, and prove provenance for all credit data **before** facts or rules.

## Observed today

- Provider calls via `IntegrationRouter` / Equifax / Karza / GST / ITR / AA  
- Results often in `kyc_step_results.parsedData` or documents  
- No unified source registry; rules sometimes keyed to provider-shaped maps  
- Silent gap defaults later erase “missing source”

## Target responsibilities

| Concern | Design |
|---|---|
| Provider adapter | One adapter per provider/API; maps to canonical **source types** |
| Ingestion request | Idempotent job: application_id, source_type, purpose, consent_ref |
| Ingestion run | Status: REQUESTED→RUNNING→SUCCEEDED/FAILED/PARTIAL |
| Source record | Logical source instance (bureau pull #3, GST FY24, AA consent window) |
| Source artifact | Immutable blob/object (raw XML/JSON/PDF) + checksum |
| Parser / normalizer | Produce candidate normalized structures — **not** facts yet |
| Quality / freshness | Scores + as_of / stale_after |
| Consent / purpose | Bound on ingest; reject if missing for regulated sources |
| Versioning | Artifact immutable; re-parse creates new parse version |
| Retention / deletion | Purpose-linked; cascade to AI working copies |
| Masking | PII views for support; raw vault restricted |

## Supported source families

Account Aggregator · bank statement API · uploaded statements · consumer/commercial bureau · ITR · AIS · 26AS · GST · MCA · CKYC · PAN/Aadhaar verify · AML/PEP/sanctions · financial statements · invoices · PO/GRN · ERP · collateral · device/fraud · manual declaration · **AI_GENERATED_CANDIDATE** (candidate only)

## Canonical records (logical)

```text
SourceRecord {
  source_id, tenant_id, application_id?, entity_id?,
  source_type, provider_code, purpose, consent_id,
  status, quality_score, as_of, stale_after,
  checksum, idempotency_key, created_at
}

SourceArtifact {
  artifact_id, source_id, content_uri, content_type,
  byte_checksum, immutable=true, retained_until
}

IngestionRun {
  run_id, request_id, adapter, attempts, error_code,
  started_at, finished_at, artifact_ids[]
}
```

## Rules

1. Raw provider data is **immutable**.  
2. Rules/metrics **never** bind to provider payload paths — only to facts/metrics.  
3. Re-ingest = new artifact + new source_record version link.  
4. AI candidates create `source_type=AI_CANDIDATE` and cannot skip Fact Engine acceptance.

## API sketch

- `POST /v1/sources/ingest`  
- `GET /v1/sources/{sourceId}`  
- `GET /v1/sources/{sourceId}/artifacts/{artifactId}` (authorized)  
- `POST /v1/sources/{sourceId}/reparse`

## Current adapters to wrap

EquifaxBureauProvider, Karza/Authbridge KYC, GstAnalysisService, ItrReturnFormsService, AA/Setu, document OCR — become Source Engine adapters emitting registry rows.

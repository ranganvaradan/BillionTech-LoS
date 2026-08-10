# 08 — AI-LOS Integration (Target)

## Observed

- One-way ingest + browser deep-link (`AiLosIntegrationService`)  
- Scalars only; `ai_decision` ignored by LOS  
- No accept/reject; demo URL on failure  
- EWS webhook to LOS unimplemented  

## Target assistive loop

```text
LOS fact snapshot + source refs + docs
        → AI analysis request
        → AI-LOS
        → typed AI outputs
        → LOS review UI
        → accept | reject | edit
        → (FACT_CANDIDATE → AI_ACCEPTED → new fact snapshot)
```

## Output classes

```text
NARRATIVE | OBSERVATION | FACT_CANDIDATE | QUESTION | ANOMALY | EXPLANATION | SCENARIO
```

Mandatory envelope fields: request_id, output_id, application_id, type, model provider/name/version, prompt/template version, generated_at, confidence, limitations, evidence, source_refs, rationale, human_review_required.

## LOS review states

```text
received → pending_review → accepted | rejected | edited → promoted → superseded
```

## Rules

1. Only accepted/validated FACT_CANDIDATEs enter a **new** fact snapshot.  
2. OBSERVATION/ANOMALY may create tasks; **must not** directly FAIL hard rules.  
3. NARRATIVE never becomes VERIFIED fact.  
4. No AI-LOS direct DB writes to LOS.  
5. Underwriting completes if AI unavailable (degrade assistive features only — **no demo score injection**).  
6. Prefer document/fact **references** over PII duplication.

## APIs

- LOS → AI: `POST /v1/analysis/requests` (idempotent)  
- AI → LOS: callback `POST /v1/ai-outputs` or poll  
- LOS: `POST .../ai-outputs/{id}/accept|reject|edit`  

## Retire

Demo fallback URL as primary failure UX for credit; treating AI-LOS ML decision as authority; logging full PII payloads at INFO.

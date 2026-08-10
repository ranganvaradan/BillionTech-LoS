# 10 — BillionTechLOS ↔ AI-LOS Integration

**Status:** Investigation (current state + target principles)  
**Primary repo:** `BillionTechLoS`  
**Sibling repo:** `AI-LOS` (`d:\CurrentAug032025\Platform\AI-LOS`)  
**System-of-record principle:** BillionTechLOS decides. AI-LOS assists. Humans approve. Every conclusion is evidence-linked and reproducible.

---

## Answers to required architecture questions

| # | Question | Finding |
|---|---|---|
| 1 | How does BillionTechLOS currently call AI-LOS? | HTTP `POST` from `AiLosIntegrationService.initiateOpen` to configured `ingest-url` (`/api/los/ingest`) with HS256 Bearer JWT. Initiated via UI → `POST /applications/{id}/ai-los/open`. |
| 2 | Which platform initiates an analysis request? | BillionTechLOS (human underwriter clicks “Open AI Review”). No workflow auto-step, no AI-LOS pull of LOS applications. |
| 3 | What payload is sent to AI-LOS? | Normalized scalars only: `loan_id`, `borrower_name`, `loan_amount`, `loan_purpose`, `credit_score`, `annual_income`, `employment_type`, tenure months/days, `existing_emi`, `property_value`, `source_los`, `source_loan_ref`. |
| 4 | Raw documents, normalized data, or both? | Normalized scalars derived from `LoanApplication` + `CreditControlService` effective context. **No** document bytes, OCR blobs, or bureau XML. |
| 5 | Where are AI-generated results stored? | In **AI-LOS DB** (`LoanApplication`, `LoanFeatures`, `UnderwritingDecision`, `AuditLog`). BillionTechLOS stores only audit events (`AI_LOS_*`), not decision artifacts. |
| 6 | Can AI-LOS update BillionTechLOS data directly? | **No** implemented path. EWS alerter in AI-LOS posts to `{LOS}/tasks/create`, but BillionTechLOS has no such endpoint. |
| 7 | Are AI outputs distinguishable from verified source facts? | In BillionTechLOS: AI outputs are not merged into application facts, so they never become facts. In AI-LOS they live in separate decision tables. |
| 8 | Are confidence, evidence, and model versions stored? | In AI-LOS yes (`confidence`, `shap_values`, `model_version`, `reason_text`). BillionTechLOS **does not** read or persist these fields from the ingest response. |
| 9 | What happens when AI-LOS is unavailable? | BillionTechLOS returns a **demo** review URL (`loan-review/TEST001`); UI still opens a tab. Underwriting continues. |
| 10 | Can BillionTechLOS complete underwriting without AI-LOS? | **Yes.** AI Review is optional; KYC/bureau/rules/scorecards gate underwriting independently. |
| 11 | Are any credit decisions dependent solely on AI output? | **No.** Local rule engine / scorecard / human actions decide. Ingest `ai_decision` / `ai_score` are ignored by Java LOS. |
| 12 | Are deterministic calculations duplicated? | **Partially.** AI-LOS `_ingest_to_features` recomputes LTV, monthly income proxies, etc. from ingest scalars; BillionTechLOS runs separate scorecard/FOIR/limit-sizing engines. No shared library. |
| 13 | Are prompts and model configurations versioned? | Not in BillionTechLOS. AI-LOS stores `model_version` on its decision row; no cross-platform prompt registry in this integration. |
| 14 | Can an analyst accept, reject, or edit an AI recommendation? | **No** BillionTechLOS API/UI for accept/reject/edit of AI outputs. Analyst may view AI-LOS UI in a new tab; credit actions remain local. |
| 15 | Is acceptance of an AI output audited? | **No** acceptance flow → no acceptance audit. Only initiate/success/fail/redirect audits exist. |
| 16 | Is applicant data unnecessarily duplicated? | **Yes**, by design of ingest: selected fields (and AI-LOS `raw_payload`) are copied into AI-LOS. No sync back. |
| 17 | Consent and data-retention consistently enforced? | **No** AI-LOS-specific consent or retention controls in BillionTechLOS for this path. |
| 18 | Can AI outputs be reproduced later? | From BillionTechLOS: **no** (payload only in logs). From AI-LOS: partially (`input_hash`, `trace_id`, `model_version` on its side). |
| 19 | Are AI suggestions used in rule evaluation? | **No.** |
| 20 | How should accepted AI-derived observations become controlled facts? | **Not implemented today.** Target: explicit acceptance workflow in BillionTechLOS before any AI-structured value enters the fact store or rules engine (see Target integration architecture). |

---

## Current integration

### Systems involved

| System | Role today |
|---|---|
| BillionTechLOS UI (`ui-service`) | Underwriter launches AI Review |
| BillionTechLOS core (`los-core-service`) | Builds payload, authenticates, calls ingest, audits, returns redirect URL |
| AI-LOS underwriting service | Ingests loan, scores, persists decision, returns `review_url` (+ `ai_decision` / `ai_score` unused by LOS) |
| AI-LOS UI | Separate browser app opened via deep link |
| AI-LOS EWS alerter | Optional outbound webhook to LOS `/tasks/create` — **not implemented on LOS** |

### API calls

```text
Browser (UnderwritingSection)
  POST /applications/{applicationId}/ai-los/open
    body: { returnUrl, mode?: "REVIEW" | "WHAT_IF" }
       ↓
LoanApplicationController.openAiLos
       ↓
AiLosIntegrationService.initiateOpen
       ↓
POST {los.integration.ai-los.ingest-url}
  default: https://ai-los.billiontech.ai/api/los/ingest
  Authorization: Bearer <HS256 JWT>
  Content-Type: application/json
       ↓
AI-LOS los_ingest → LOSIngestResponse
       ↓
AiLosOpenResponse → window.open(finalRedirectUrl)
```

**Key files (BillionTechLOS):**

- `los-core-service/.../AiLosIntegrationService.java`
- `los-core-service/.../LoanApplicationController.java` (`POST .../ai-los/open`)
- `los-core-service/.../IntegrationProperties.AiLosProperties`
- `los-core-service/src/main/resources/application.yml` (`los.integration.ai-los.*`)
- `ui-service/src/api/applications.ts` (`openAiLosReview`)
- `ui-service/src/components/UnderwritingSection.tsx` (`onOpenAiReview`)

**Key files (AI-LOS):**

- `ai-layer/services/underwriting/app/routers/los_ingest.py`
- `ai-layer/services/ews/app/jobs/alerter.py` (unimplemented LOS webhook consumer)

### Events

No message-bus events. Integration is synchronous HTTP only.

Audit events in BillionTechLOS (`eventType = AI_LOS`):

| Action | When |
|---|---|
| `AI_LOS_REQUEST_INITIATED` | Before outbound POST |
| `AI_LOS_REQUEST_SUCCESS` | HTTP 2xx, or 409 existing-loan deep-link path |
| `AI_LOS_REDIRECTED` | After building `finalRedirectUrl` |
| `AI_LOS_REQUEST_FAILED` | Non-2xx, timeout, or other errors (then demo fallback) |

### Payloads

**Request (LOS → AI-LOS)** — snake_case JSON (`AiLosIngestRequest`):

```json
{
  "loan_id": "<applicationNumber or UUID>",
  "borrower_name": "<string>",
  "loan_amount": 100000,
  "loan_purpose": "Working Capital",
  "credit_score": 650,
  "annual_income": 240000,
  "employment_type": "salaried|self_employed|business",
  "loan_tenure_months": 12,
  "existing_emi": 15000,
  "property_value": 100000,
  "source_los": "java-los",
  "source_loan_ref": "<same as loan_id>"
}
```

Missing values are replaced with hardcoded defaults in `AiLosIntegrationService` (amount 100000, score 650, income 240000, EMI 15000, property 100000, purpose “Working Capital”, tenure 12).

**Response (AI-LOS → LOS)** — AI-LOS returns:

```json
{
  "loan_id": "...",
  "status": "ingested",
  "ai_decision": "APPROVE|DECLINE|...",
  "ai_score": 0.0,
  "review_url": "https://.../loan-review?loanId=...&returnUrl=",
  "message": "..."
}
```

BillionTechLOS **reads only** `status`, `message`, `review_url`. It does **not** persist or act on `ai_decision` / `ai_score`.

**UI response** (`AiLosOpenResponse`): `loanId`, `status`, `message`, `reviewUrl`, `finalRedirectUrl`, `mode`.

### Authentication

| Hop | Mechanism |
|---|---|
| UI → BillionTechLOS | Existing LOS HTTP session/API auth (endpoint itself has no dedicated role annotation) |
| BillionTechLOS → AI-LOS | HS256 JWT: claims `sub` = `jwt-subject` (default `java-los`), `role` = `jwt-role` (default `integration`); secret `AI_LOS_JWT_SECRET` (default `dev_secret`). **No `exp` claim** in current Java JWT builder. |

### Request initiation

- Manual only from Underwriting UI (“Open AI Review”).
- Mode hardcoded to `REVIEW` in UI (WHAT_IF supported in DTOs/config but not used by the button).
- `returnUrl` = `window.location.href`.

### Response handling

1. On success: append encoded `returnUrl` to `review_url` when both present; open in new tab.
2. On 409 “loan_id already exists”: build deep link `{uiBaseUrl}/loan-review?loanId=...&returnUrl=...`.
3. On other failures: return demo URL (`https://ai-los.billiontech.ai/loan-review/TEST001`).

### Persistence

| Where | What |
|---|---|
| BillionTechLOS | Audit log rows only; no AI decision / observation entities |
| AI-LOS | `LoanApplication` (incl. `raw_payload`), `LoanFeatures`, `UnderwritingDecision` (`confidence`, `shap_values`, `model_version`, `reason_text`, `counteroffer`), `AuditLog` (`input_hash`, `trace_id`, latency) |

### Retries

None. Single HTTP attempt.

### Timeouts

Config defaults: connect **8000** ms, read **15000** ms (`AI_LOS_CONNECT_TIMEOUT` / `AI_LOS_READ_TIMEOUT`). Enforced in `AiLosIntegrationService.@PostConstruct`.

### Failure handling

Fail-open to demo review URL so the UI always gets a redirect target. Underwriting workflow is unaffected. Failures are audited and logged.

### Audit logging

See Events table above. Application logs also emit `[AI_LOS_REQUEST_PAYLOAD]` (full JSON at INFO — sensitive in production).

---

## Responsibility matrix

| Capability | BillionTechLOS | AI-LOS | Shared or Integration |
|---|---|---|---|
| Application management | **Owner** | Shadow copy on ingest only | Ingest copies selected fields |
| Raw-data ingestion | **Owner** (docs, APIs, KYC, bureau) | Not used for LOS docs today | — |
| Source-data storage | **Owner** | Stores ingest `raw_payload` duplicate | Duplication risk |
| Data normalization | **Owner** (application maps, OCR extracts, credit control) | Re-derives features from scalars | Competing transforms |
| Deterministic calculations | **Owner** (scorecard, FOIR, limit sizing, rules) | Separate LTV/income proxies for ML features | Duplicated logic |
| Policy rules | **Owner** (`UnderwritingRuleEngine`, scorecards) | ML decision thresholds / scorer | Must not compete for authority |
| Risk scoring | Policy/scorecard risk in LOS | ML `risk_score` / `ai_score` in AI-LOS | Parallel, not linked |
| Document interpretation | OCR / Karza / GST / ITR providers in LOS | Capability exists in AI-LOS platform; **not** wired via current ingest | Future integration surface |
| Anomaly detection | Limited / provider-driven | EWS / model explanations | Webhook unfinished |
| Underwriting narrative | Local CAM / credit appraisal services | Reasons / SHAP / review UI | Not imported into LOS |
| Final recommendation | **Owner** (rules + humans) | Suggests `ai_decision` (ignored by LOS) | Integration must keep LOS authoritative |
| Human approval | **Owner** | Analyst may view AI UI only | No accept/reject bridge |
| Sanction | **Owner** | None | — |
| Audit trail | LOS audit for call lifecycle | AI-LOS audit for scoring | Split; incomplete cross-link |
| Monitoring | LOS logs/audits | AI-LOS service metrics | No shared SLO dashboard in-repo |

---

## Target integration architecture

Aligned with: **BillionTechLOS decides; AI-LOS assists; humans approve; conclusions are evidence-linked and reproducible.**  
Do **not** move the core rules engine into AI-LOS.

### Proposed API boundaries

| Direction | Contract | Purpose |
|---|---|---|
| LOS → AI-LOS | `POST /v1/analysis/requests` (evolution of ingest) | Submit analysis job with references to LOS source artifacts + optional normalized snapshot |
| AI-LOS → LOS | `POST /v1/ai-outputs` (callback) or LOS poll `GET /v1/analysis/requests/{id}` | Deliver structured AI outputs with confidence/evidence/model metadata |
| LOS internal | `POST /applications/{id}/ai-outputs/{outputId}/accept\|reject\|edit` | Human gate before facts/rules may use AI-derived values |
| Forbidden | AI-LOS direct DB write to LOS; silent overwrite of facts; AI approve/reject/sanction | Preserve system-of-record |

### Proposed event boundaries (optional async)

```text
los.ai.analysis.requested
los.ai.analysis.completed
los.ai.output.accepted
los.ai.output.rejected
```

Events carry correlation IDs only; payloads stay in APIs/stores.

### Canonical request envelope (LOS → AI-LOS)

```json
{
  "request_id": "uuid",
  "idempotency_key": "string",
  "application_id": "uuid",
  "loan_ref": "string",
  "requested_at": "ISO-8601",
  "analysis_types": ["BUREAU_SUMMARY", "DOC_EXTRACTION", "ANOMALY", "NARRATIVE"],
  "inputs": {
    "normalized_snapshot_ref": "los://...",
    "document_refs": [{ "document_id": "uuid", "type": "ITR", "storage_uri": "..." }],
    "metric_snapshot_ref": "los://..."
  },
  "privacy": { "purpose": "UNDERWRITING", "consent_ref": "..." },
  "callback": { "url": "https://los.../v1/ai-outputs", "auth": "m2m" }
}
```

Prefer **references** (document IDs / storage URIs / fact-set versions) over re-sending full PII blobs when possible.

### Canonical response envelope (AI-LOS → LOS)

```json
{
  "request_id": "uuid",
  "output_id": "uuid",
  "application_id": "uuid",
  "generated_at": "ISO-8601",
  "model": { "provider": "...", "name": "...", "version": "..." },
  "analysis_type": "BUREAU_SUMMARY",
  "status": "SUCCEEDED",
  "confidence": 0.0,
  "human_review_required": true,
  "limitations": ["..."],
  "claims": [
    {
      "claim_id": "uuid",
      "kind": "OBSERVATION|METRIC_CANDIDATE|NARRATIVE|QUESTION",
      "path": "underwriting.observations.cheque_bounce_pattern",
      "value": {},
      "evidence": [{ "source_ref": "los://document/...", "excerpt_ref": "..." }],
      "supporting_rationale": "..."
    }
  ]
}
```

Every claim must carry confidence, evidence, source references, model version, timestamp, limitations, and `human_review_required`.

### Idempotency

- Client `idempotency_key` on analysis request (e.g. `applicationId + analysisTypes + factSetVersion`).
- AI-LOS dedupe; LOS stores request/response by `request_id` / `output_id`.
- Accept/reject operations idempotent by `output_id` + action.

### Data ownership

| Data class | Owner |
|---|---|
| Application state, source registry, raw sources, normalized facts, deterministic metrics, policy versions, rule results, human decisions, sanction | **BillionTechLOS** |
| Model artifacts, prompts, intermediate AI traces, draft narratives | **AI-LOS** (or shared artifact store referenced by LOS) |
| Accepted AI claims promoted to facts | **BillionTechLOS** after explicit acceptance |

### AI-output ownership

AI-LOS owns generation. BillionTechLOS owns lifecycle after delivery: received → reviewed → accepted/rejected/edited → linked to underwriting version.

### Evidence references

Accepted claims must store immutable pointers to LOS source documents / fact versions used as evidence, not only free-text rationale.

### Model-version tracking

Persist on every stored AI output in BillionTechLOS: provider, model name, version, prompt/template version (if applicable), request hash.

### Confidence handling

- Below policy threshold → force `human_review_required`.
- Rules engine consumes **only** accepted values that passed validation, never raw confidence alone.
- Confidence stored for audit/explainability, not as a silent decision switch.

### Timeout and fallback behaviour

- Analysis optional for core underwriting completion (preserve today’s independence).
- Timeouts: fail analysis request, surface “AI unavailable” in UI; **do not** substitute demo scores into policy.
- Retries with backoff on transport errors; no auto-accept.

### Manual-review workflow

1. AI output arrives (callback/poll).  
2. Displayed in BillionTechLOS review UI as AI-sourced (visually distinct from verified facts).  
3. Analyst accept / reject / edit.  
4. On accept: write controlled fact or observation with acceptance metadata.  
5. Only then may rules/metrics that depend on that fact re-run.

### Security and privacy controls

- Short-lived m2m tokens with expiry (replace current no-`exp` JWT).  
- Rotate shared secrets; separate from user auth.  
- Minimize PII in outbound payloads; prefer refs.  
- Align retention/deletion of AI-LOS copies with LOS applicant deletion.  
- Consent purpose tags on analysis requests.  
- Stop logging full PII payloads at INFO in production.

---

## Duplication and conflict analysis

| Issue | Evidence |
|---|---|
| Logic duplicated | AI-LOS `_ingest_to_features` builds LTV / income proxies; LOS has `CreditControlService`, scorecards, limit sizing |
| Competing data models | LOS `LoanApplication` + jsonb bags vs AI-LOS `LoanApplication` / `LoanFeatures` / `UnderwritingDecision` |
| Conflicting calculations | Possible different LTV/income if defaults or field mapping diverge; decisions not reconciled |
| Duplicated storage | Ingest copies scalars + AI-LOS stores `raw_payload`; no lifecycle sync on LOS delete |
| Inconsistent statuses | LOS application status vs AI-LOS `INGESTED` / ML decision — independent |
| Inconsistent identifiers | LOS UUID vs `applicationNumber` as `loan_id` / `los_loan_id` (truncated to 64 in AI-LOS) |
| Direct database dependencies | None between platforms (HTTP only) — good |
| Tightly coupled code | Thin; coupling is fragile defaults + ignored response fields + demo fallback |
| AI-LOS treated as system of record | Not for LOS decisions today (good). Risk if future UI starts treating AI `ai_decision` as authoritative without acceptance. Demo URL on failure can mislead analysts into viewing unrelated TEST001 case |
| Unfinished reverse integration | EWS webhook to `/tasks/create` has no LOS consumer — half-wired coupling |

---

## Target operating principle

```text
BillionTechLOS decides.
AI-LOS assists.
Humans approve.
Every conclusion is evidence-linked and reproducible.
```

### Rearchitecture boundary (confirmed)

**Remain / grow in BillionTechLOS**

- Canonical underwriting schema; source registry; normalized fact store  
- Deterministic metric and reconciliation engines  
- Credit-policy definitions; rule execution; policy versioning; simulation  
- Exception/override; decision orchestration; approval authorities; sanction; full decision audit  

**Remain / grow in AI-LOS**

- Unstructured extraction, semantic classification, narration enrichment  
- Anomaly / inconsistency explanations, risk narratives, credit-note drafts  
- Scenario narratives, analyst copilot, NL policy assistance, recommendation explanations  

**Mandatory gate:** Any AI-LOS structured value that may affect underwriting must pass BillionTechLOS validation and **explicit human acceptance** before the rules engine may use it.

---

## Gap summary (current vs principle)

| Principle requirement | Current state |
|---|---|
| LOS authoritative for decisions | Met for credit decisions (AI ignored) |
| AI assists with extraction/narrative | Not via this integration (ingest = scalar score+review deep-link only) |
| Humans approve AI outputs | Missing |
| Confidence / evidence / model version on all AI outputs in LOS | Missing in LOS |
| No silent promotion to facts | Met by absence of import |
| Reproducibility | Weak on LOS side |
| Complete underwriting without AI | Met |
| No AI-only credit decision | Met |

---

## Related investigation notes

Prior rule-engine investigations in this repo (`RULE_ENGINE_INVESTIGATION.md`, parts 2–3) establish that policy execution is entirely local to BillionTechLOS and that `AiLosIntegrationService` is an external deep-link/ingest client, not a rule or primitive authoring path.

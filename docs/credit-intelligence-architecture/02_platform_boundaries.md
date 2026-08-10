# 02 — Platform Boundaries

## Governing principle

```text
BillionTechLOS decides.
AI-LOS assists.
Humans approve.
Every conclusion is evidence-linked and reproducible.
```

## BillionTechLOS owns

| Domain | Ownership |
|---|---|
| Loan application & workflow | Authoritative |
| Applicant & related-party data | Authoritative (with identity service) |
| Source registry & raw artifacts | Authoritative |
| Consent & purpose | Authoritative |
| Normalized facts & snapshots | Authoritative |
| Deterministic metrics & reconciliations | Authoritative |
| Policy definitions, versions, packages | Authoritative |
| Rule / scorecard / limit / pricing execution | Authoritative |
| Decision recommendation | Authoritative |
| Manual review, deviations, overrides | Authoritative |
| Approval authority matrix | Authoritative |
| Sanction & post-sanction conditions | Authoritative |
| Decision audit | Authoritative |
| Monitoring state & EWS **action** workflow | Authoritative |
| LMS / payment / accounting handoff | Authoritative |

## AI-LOS may own

| Domain | Ownership |
|---|---|
| Unstructured document interpretation | Assistive |
| Semantic extraction / narration enrichment | Assistive |
| Anomaly & inconsistency suggestions | Assistive |
| Risk narratives, UW summaries, credit-note drafts | Assistive |
| Analyst copilot, scenario narratives | Assistive |
| NL policy assistance, recommendation explanations | Assistive |
| Model training artifacts & prompt versions | AI-LOS (referenced from LOS) |

## AI-LOS must not

- Approve, reject, sanction, or disburse  
- Modify verified facts or overwrite deterministic metrics  
- Bypass hard rules or approve deviations  
- Write directly to LOS databases  
- Silently become system of record  

## Capability matrix

| Capability | BillionTechLOS | AI-LOS | Shared / integration |
|---|---|---|---|
| Application management | **Owner** | Shadow copy only if needed | Refs by application_id |
| Raw-data ingestion | **Owner** | Optional OCR assist | LOS initiates / stores raw |
| Source-data storage | **Owner** | Temp working copies | Retention aligned |
| Normalization / facts | **Owner** | FACT_CANDIDATE only | Accept gate in LOS |
| Deterministic metrics | **Owner** | Must not duplicate for decisions | — |
| Policy rules | **Owner** | NL assistance only | — |
| Risk scoring (policy) | **Owner** | ML scores as observation | Accept ≠ auto-score |
| Document interpretation | Orchestrates | **Produces candidates** | API + evidence |
| Anomaly detection | Consumes / acts | **Suggests** | Tasks in LOS |
| Underwriting narrative | Stores accepted | **Drafts** | Review UI in LOS |
| Final recommendation | **Owner** | Explains | — |
| Human approval | **Owner** | — | — |
| Sanction | **Owner** | — | — |
| Audit trail | **Owner** | Model-side traces | Correlation IDs |
| Monitoring actions | **Owner** | Signal suggestions | Event/API |
| Simulation / back-test | **Owner** | Optional portfolio analytics | Frozen LOS snapshots |

## Trust boundary

```text
[Untrusted] External providers, documents, AI models
      ↓ TLS + adapter auth
[LOS Source Engine] immutable raw
      ↓ normalize
[LOS Fact/Metric/Policy/Decision] system of record
      ↓ m2m short-lived tokens
[AI-LOS] assistive compute
      ↓ callback / poll with envelopes
[LOS AI review] human gate
```

See `diagrams/02_trust_boundaries.mmd`.

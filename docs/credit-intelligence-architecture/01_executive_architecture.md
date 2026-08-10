# 01 — Executive Architecture

**Platform:** BillionTech Credit Intelligence (underlying BillionTechLOS)  
**Baseline:** Current development codebase (not yet production)  
**Principle:** BillionTechLOS decides · AI-LOS assists · Humans approve · Every conclusion is evidence-linked and reproducible

---

## Distinguishing layers

| Layer | Meaning |
|---|---|
| **Observed (current code)** | Flat `EffectiveUnderwritingContext.scorecard` bag; mutable `rules_json` / scorecards; implicit orchestration in `LoanApplicationFlowService`; gap defaults; AI-LOS deep-link ingest only |
| **Retained** | Hard-rule / scorecard engines (evolve), `underwriting_evaluations`, limit-sizing pattern, workflow/CAM/sanction, provider adapters, Flyway |
| **Target** | Six engines + supporting services; immutable sources/facts/policies; explicit orchestration; DATA_INSUFFICIENT; AI gate |
| **Transitional** | Shadow evaluation; adapters wrapping current JSONB policies; dual-write of evaluations |
| **Retire** | Silent gap defaults on policy path; demo AI fallback as decision UX; parallel `CreditRulesEngine` / enhancement matrix as alternate authority; provider-coupled rule keys |

---

## Ten-point target architecture

1. **Source Engine** ingests AA, bureau, GST, ITR, docs, manual, ERP, AI candidates into immutable raw artifacts + provider-neutral source records.  
2. **Identity service** resolves borrowers, parties, accounts, GSTIN/PAN subjects with confidence and human merge.  
3. **Fact Engine** materializes classified underwriting facts (`VERIFIED`…`DEFAULTED`) into immutable **fact snapshots**.  
4. **Metric Engine** computes versioned metrics via DAG (banking, bureau, tax, WC, structuring) with explicit missing-data policy.  
5. **Reconciliation Engine** produces cross-source MATCH / VARIANCE / CONFLICT / DATA_INSUFFICIENT outcomes.  
6. **Policy Engine** evaluates immutable **policy packages** (hard/soft/refer/DQ/scorecard/limit/pricing/authority/monitoring) with evidence-aware DSL.  
7. **Decision Engine** assembles recommendations (limit, price, tenure, conditions, covenants) without replacing human approval.  
8. **Orchestrator** runs declarative stages (data readiness → approval authority), not Java call order alone.  
9. **AI-LOS** returns typed outputs; only **accepted** FACT_CANDIDATEs enter new fact snapshots; LOS never lets AI approve/sanction.  
10. **Monitoring Engine** consumes recurring sources for EWS, covenants, renewal — same fact/metric/policy patterns post-disbursement.

---

## Six primary engines

```text
Source → Fact → Metric + Reconciliation → Policy → Decision → Monitoring
```

Supporting: identity, consent, evidence/lineage, policy lifecycle, simulation/back-test, overrides, AI integration, audit, notifications, orchestration.

---

## Lifecycle coverage

```text
acquisition → verification → normalization → facts → metrics → reconciliation
→ policy → scoring → structuring → human decision → sanction
→ monitoring → EWS → review/renewal
```

---

## Most important architectural decision

**Immutable fact snapshots + immutable policy packages are the only inputs to credit evaluation.**  
Without this, reproducibility, shadow migration, AI gating, and regulator-grade audit cannot be achieved. All other engines depend on it.

---

## First implementation task after approval

Implement **Foundation / Milestone 1**: source registry (minimal) + fact snapshot on underwrite + policy version publish + standard result schema + **shadow evaluation** with production decisions unchanged (aligned with `docs/architecture-review/13_rules_engine_milestone_1.md`).

---

## Document index

| # | Document |
|---|---|
| 02 | Platform boundaries |
| 03 | Source Engine |
| 04 | Identity and Fact Engine |
| 05 | Metric and Reconciliation |
| 06 | Policy Engine |
| 07 | Decision Engine |
| 08 | AI-LOS integration |
| 09 | Monitoring Engine |
| 10 | Data model |
| 11 | API and events |
| 12 | Security and governance |
| 13 | Migration roadmap |
| 14 | Retain / refactor / replace |
| 15 | Architecture decision records |
| diagrams/ | Mermaid sources |

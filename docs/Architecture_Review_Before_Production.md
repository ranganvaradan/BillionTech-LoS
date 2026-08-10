# Architecture Review Before Production

**Platform:** BillionTech Credit Intelligence (underlying BillionTechLOS)  
**Reviewer stance:** Chief Software Architect — independent of the implementing team  
**Scope reviewed:** `creditintelligence/` (F → C5), production hooks (`LoanApplicationFlowService`, `CreditControlService`, `LimitSizingService`), Flyway **V86–V97**, architecture docs (01–15), implementation docs (01–29), provider adapters (Equifax, Karza GST/ITR; SurePass **not present in repo**)  
**Codebase scale observed:** ~204 Java classes / ~20.5k LOC under `creditintelligence/`; 170+ package tests; feature flags default **off**  
**Date of review posture:** Pre-production; never gone live  
**Verdict (see §13):** **YES WITH CHANGES**

---

## Executive Summary

The **target architecture is fundamentally correct** for a regulated Indian lending underwriting platform: immutable sources → classified facts → versioned metrics → cross-source reconciliation → immutable policy packages → decisions with evidence and human authority, AI assistive only. That spine matches how serious credit platforms survive audits and multi-year product evolution.

What exists today is **not yet that architecture in production form**. It is a large, carefully flag-gated **shadow dual-write sidecar** (~20k LOC) bolted onto a still-authoritative legacy stack (`CreditControlService` scorecard bag, silent SCF/retail gap defaults, Java orchestration in `LoanApplicationFlowService`, mutable live rule sets).

The most important architecture contract in the docs —

> *Immutable fact snapshots + immutable policy packages are the only inputs to credit evaluation.*

— is **not delivered**. Policy versions are **recorded** but shadow still executes `UnderwritingRuleEngine` against **live** active rule rows; thresholds often come from **runtime YAML**; clocks use `LocalDate.now()` / `Instant.now()`; the adapter can overlay **latest live** `CiMetricResult` onto a frozen snapshot. Replay exists as an API, not as a purity guarantee.

Relative to FICO / Experian Decisioning / Provenir / Taktile / Perfios / Moody’s / nCino **architecture** (not feature parity): BillionTech has a credible **evidence + reconciliation + source-family** story and better-than-typical honesty about DATA_INSUFFICIENT. It is behind on **policy-as-sole-input**, **tenant enforcement**, **metric DAG / formula registry**, **unified evidence**, **provider abstraction maturity**, and **live multi-source validation**.

**Do not put this in front of production underwriting authority as-is.**  
**Do keep building on this direction** after fixing the contract gaps below. Redesigning from zero would waste the correct domain investments (GST periods, bank txn taxonomy, ITR form semantics, recon definitions). Repair the spine; do not scrap the organs.

**Architecture score: 6.5 / 10** (target design ~8.5; implemented fidelity ~5.5; blended)  
**Production readiness score: 4.0 / 10** (as *authoritative* underwriting engine)  
**As continued shadow / dual-write: ~6.5 / 10** (operationally cautious, incomplete validation)

---

## 1. Overall Architecture

### Is it fundamentally correct?

**Yes — as a target.** Source → Fact → Metric + Reconciliation → Policy → Decision (with Monitoring later) is the right decomposition for India MSME / SCF underwriting with AA, GST, ITR, and bureau.

### Over-engineered?

**Partially yes.** Ten engines in docs vs a monorepo of Java services; reconciliation definition tables plus still-hand-coded evaluators; dual storage of the same variances (C2/C4 metrics **and** C5 wraps); heavy JSONB “escape hatches”; per-family admin APIs before a single credit-evidence UX contract is productized.

### Under-engineered?

**Also yes — on the hard parts:** identity/entity resolution, true policy execution from frozen packages, Clock/config freeze for replay, DB-enforced multi-tenancy, metric formula DAG, LimitSizing as a versioned metric pack, AI accept/reject loop, monitoring/EWS, and **live multi-source validation** (still largely synthetic/fixture).

### Elegant

- Explicit **DATA_INSUFFICIENT** vs inventing zeros (C1–C5 direction).
- **Source Registry** + artifact checksum thinking.
- Form-sensitive ITR / presumptive separation.
- **Period alignment first-class** in C5 (correct instinct; rare in LOS codebases).
- Shadow-only cutover discipline and fail-soft hooks (good for migration risk).
- Reconciliation **catalogue** and evidence-strength **separated from credit risk**.

### Forced

- `compat.*` scorecard dump as the real bridge to legacy engines.
- `CiMetricResult` living under **bureau** package as global bus with nullable `bureau_report_id`.
- `LegacyReconciliationBridge` instead of retiring family-local `xsrc.*` metrics.
- Snapshot “freeze” while shadow overlays live metrics and live rules.
- Default `tenant_id = 000…0001` everywhere.

### Unnecessary abstractions (today)

- Parallel shadow rule evaluators that re-encode thresholds already in YAML **without** binding to policy package content.
- Evidence strength flag separate from recon enablement without a clear product consumer.
- Multiple admin controllers before a unified “credit evidence” read model for underwriters.

### Missing abstractions

- **EvaluationContext** = `{ snapshotId, policyVersionId, asOfClock, configFreezeId }` — sole input to metrics/rules/recon.
- **Identity / Party graph** (borrower vs promoter vs guarantor accounts/GSTINs/PANs) as a first-class aggregate.
- **Provider Adapter SPI** with contract tests (Equifax / CIBIL / CRIF / Karza / Setu / future SurePass).
- **Formula / Metric Registry** actually executed (defs exist; Java services are the real engine).
- **Unified EvidenceStore** (not banking-only `CiEvidenceGroup` + empty JSON lists elsewhere).
- **Declarative orchestration** (ADR-007 still unmet).

---

## 2. Domain Model Review

| Aggregate | Why it exists | Should it? | Merge / split | DDD / scale notes |
|-----------|---------------|------------|---------------|-------------------|
| `CiSourceRecord` + artifact | Immutable raw evidence | **Yes** | Keep; strengthen write-once enforcement | Core SoR; scale with retention policy |
| `CiFactSnapshot` + `CiUnderwritingFact` | Evaluation inputs | **Yes** | Split “compat dump” from true underwriting facts over time | Snapshot bloat risk (see §4) |
| `CiPolicyPackage` / `CiPolicyVersion` | Freeze rules | **Yes conceptually** | Keep | **Broken contract:** content frozen, **not executed** |
| `CiCreditEvaluation` + stages + `CiStandardRuleResult` | Audit trail | **Yes** | Keep | Good for regulators if fidelity fixed |
| `CiMetricDefinition` / `CiMetricResult` | Versioned metrics | **Yes** | Move `CiMetricResult` out of bureau BC | Append-only growth; need snapshot-scoped uniqueness |
| Bureau report/tradeline/PH/inquiry | Tradeline truth | **Yes** | Keep | Scales; index by app+report |
| GST registration/period/financials | Period truth | **Yes** | Keep | Elegant for India GST |
| Bank account/txn/classification/obligation | AA truth | **Yes** | Keep txn tables; watch volume | **500k txns/app** needs partitioning strategy |
| `CiEvidenceGroup` | High-volume refs | **Yes** | **Promote to shared BC**; today banking-local | Incomplete |
| ITR return/income/biz/presumptive/tax + AIS/26AS | Tax truth | **Yes** | Keep form sensitivity | AIS/26AS largely empty in prod reality |
| `CiReconciliationDefinition` / `Result` | Cross-source | **Yes** | Keep definitions; collapse dual compute | Results append; index OK |
| `CiCreditEvidenceSummary` | Underwriter read model | **Yes** | Keep | Deterministic summary is right; don’t let AI own it |

**DDD violations:** Metric result in bureau package; reconciliation depending on every family’s repositories; foundation services knowing all canons; tenant as a column without a Tenant aggregate; “effective” mutable flags on ITR/GST while claiming immutability of evidence (lineage OK if originals preserved — document clearly).

---

## 3. Package Review

```text
creditintelligence/
  domain | service | api | config     ← foundation
  bureau | gst | banking | tax        ← source families
  reconciliation                      ← cross-cutting
```

**Can they survive five years?** Source-family packages **yes**, if they stop leaking into each other’s domains and share a **core** module for MetricResult, Evidence, EvaluationContext.

**Merge?** Do not merge GST+Tax+Bank into one package. Merge **shared kernel**: metrics, evidence, period types, hashing, Clock.

**Separate bounded contexts (longer term)?**

| Context | Rationale |
|---------|-----------|
| Source Ingestion | Providers, artifacts, consent |
| Credit Fact Store | Snapshots, classifications |
| Credit Analytics | Metrics + reconciliation |
| Policy & Decision | Packages, evaluation, authority |
| Monitoring | Post-disburse (not built) |

Today everything lives in **one Spring Boot service** — acceptable for Year 1–2; plan module boundaries before 100 lenders / 50 providers.

---

## 4. Database Review

### Strengths
- Sensible append/idempotency keys on source-family pulls.
- Unique “effective” indexes for GST periods / ITR returns.
- Snapshot immutability triggers (foundation).
- Reconciliation definitions versioned.

### Weaknesses
- **`tenant_id` without FK and without RLS** — application-level only; default single UUID.
- **`ci_metric_result.bureau_report_id`** nullable misuse for non-bureau metrics.
- **No uniqueness** of (snapshot_id, metric_code, version) — duplicate appends possible.
- **JSONB overuse** without schema registry (`value: {v:…}`, evidence bags).
- **`ci_evidence_group` not FK-linked** from recon `evidence_group_ids`.
- **Snapshot bloat:** every scorecard key as `compat.*` fact × every underwrite.
- Dual variance storage (metric + recon result).

### Growth estimate (order-of-magnitude)

Assumptions: 10M applications over 5 years; ~100 lenders; ~50 providers; average SME app with AA depth.

| Store | Rough row estimate | Storage order |
|-------|-------------------|---------------|
| Bank transactions | 10M × 2k–20k txns → **20B–200B rows** worst case; realistic average lower | **Dominant cost** — requires partition by tenant/month, archival, maybe cold store |
| Metric results | 10M × 50–150 metrics × re-runs → **0.5B–5B** | Large; snapshot-scoped retention policy needed |
| Recon results | 10M × 10–30 × re-runs | 0.1B–1B |
| Facts per snapshot | 10M × 1–5 snapshots × 100–400 facts | 1B–20B cells-as-rows |
| Bureau tradelines | 10M × 20–80 | manageable |
| GST periods | 10M × 12–36 | manageable |

**Without txn partitioning + retention, banking tables alone can sink the platform.** Design for that **before** cutover, not after the first large lender.

---

## 5. Performance Review

| Workload | Expected cost today | Bottleneck |
|----------|---------------------|------------|
| 500k bank txns | Classification + metrics in-process; possible full scans | Memory + N+1 if loading classifications per txn; need batch SQL / streaming |
| 500 GST periods | Fine | Minor |
| 300 tradelines | Fine | Minor |
| 10y ITR | Fine | Form parsing quality, not volume |
| Multiple recon | Fine if metrics precomputed | Operand resolver + live metric lookup |

**Risks:** `findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc` patterns; snapshot build walking entire scorecard; JSON parse of large AA payloads; shadow re-running full rule engines; `ensureIngested` side effects during snapshot; no selective metric DAG invalidation fully productized.

**Replay cost:** Re-executing live engines + re-reading latest metrics ≠ cheap historical recompute from frozen inputs.

---

## 6. Replay Review

**Is replay truly deterministic?** **No.**

Assumptions that break identical outputs six months apart:

1. Shadow / metrics / freshness use **`LocalDate.now()` / `Instant.now()`** without injectable `Clock`.
2. **`UnderwritingRuleEngine` reads live active rules**, not `ci_policy_version.policy_content`.
3. Thresholds from **`CreditIntelligenceProperties`** at runtime, not frozen with the evaluation.
4. Adapter may overlay **latest** `CiMetricResult` when `useForShadowRules`.
5. Parser/normalizer versions are string constants, not pinned hashes of code/config.
6. Gap defaults still shape `compat.*` facts if production context was defaulted.

**Until EvaluationContext is sole input, “replay” is marketing.**

---

## 7. Event Architecture

| Concern | Recommendation |
|---------|----------------|
| Source ingest complete | **Async** (already fail-soft) + durable outbox |
| Metric compute | **Event-driven selective recompute** on source/fact change (dependency metadata started in C5 — finish it) |
| Reconciliation | **Event-driven** after metric set stable; not sync inside underwrite hot path at cutover |
| Shadow evaluation | **Async by default** in prod (sync OK for tests); never block sanction |
| Underwrite decision path | Stay **sync** and thin until CI is authoritative |

**Today:** mostly sync hooks + optional shadow async. Not wrong for shadow phase; **under-evented** for 10-year scale.

---

## 8. Rules Architecture

**Rules should consume, in order of preference:**

1. **Facts** (classified, subject-scoped)  
2. **Metrics** (versioned outcomes including DATA_INSUFFICIENT)  
3. **Reconciliations** (outcomes + severity, not raw left/right alone)  
4. **Evidence references** (for explain/audit, not for branching on free text)

They should **not** consume provider JSON, scorecard gap inventions, or AI narratives.

**Better abstraction:** a small **Policy Input Interface** —

```text
require fact|metric|recon path
with outcome ∈ {…}
threshold from policy package
produce StandardRuleResult + evidenceRefs
```

Current dual path (legacy scorecard keys **and** canonical shadow evaluators) is transitional debt. Collapse to one Policy Engine reading frozen packages.

---

## 9. AI Readiness

Docs (ADR-001, ADR-009, doc 08) are **correct**: AI assistive; FACT_CANDIDATE human-gated; never sanction authority.

**Implementation:** AI accept/reject → new snapshot loop is **largely unimplemented**. Current AI-LOS remains deep-link / scalar ingest culture.

**Safe AI consumption:** Facts, metrics, reconciliations, evidence summaries — **yes**, as inputs to narrative/anomaly generation.  
**Unsafe:** Letting AI set outcomes that flow into hard rules without accept.

**Redesign?** Not the architecture — **implement the gate**. Ban demo fallback as credit UX. Prefer refs over PII in prompts.

---

## 10. API / Adapter Review

### Equifax
- Production SOAP provider + C1 `EquifaxBureauAccountExtractor` for tradelines/history.
- Fragile XPath/XML assumptions; inquiry detail often incomplete → DI (honest).
- Fields often ignored for canonicalization: full address history, ownership type nuances, written-off/settled edge enums, guarantor links — extend taxonomy carefully.
- Masking last4/hash is good; ensure raw XML always in source artifact (ADR-002).

### Karza GST / ITR
- Extractors are provider-shaped; OK transitional.
- Missing: formal **Provider SPI** + golden JSON fixtures as versioned resources (tests embed payloads).
- ITR: many heads null unless Karza supplies them — correct DI, but underwriting still gaps via CreditControl.

### SurePass
- **No SurePass references or sample payloads found in this repository.**  
- Cannot review SurePass adapters. Treat as **missing provider / missing fixture evidence** for this review. If SurePass is used in another module or environment, it is outside the inspected tree.

### Adapter abstract gaps
- No multi-bureau abstraction (Equifax-only path in CI).
- No versioned fixture corpus for regression of parsers across provider schema drift.
- Admin APIs are engineer-facing, not underwriter explainability UX (C5 started explainability shape — finish productization).

---

## 11. Competitive Architecture Comparison

*(Architecture only — not feature checklist.)*

| Dimension | BillionTech CI (today) | Typical strong peer posture |
|-----------|------------------------|-----------------------------|
| Extensibility | Good source-family split; weak SPI | Strong provider + product plugins |
| Explainability | Promising recon + evidence summary | First-class decision explanation objects |
| Auditability | Tables exist; freeze contract incomplete | Snapshot+policy sole inputs |
| Replay | API without purity | Clock + config + policy pinned |
| Regulator friendliness | DI semantics + lineage direction good | Proven immutability + tenant isolation |
| AI readiness | Docs excellent; code thin | Gated candidates in production |
| Event model | Early | Outbox + selective recompute |
| Source abstraction | Karza/Equifax coupled extractors | Neutral DTOs + certified adapters |

**Verdict vs peers:** Directionally competitive in **reconciliation + India-source depth**. Not yet competitive as a **decisioning platform kernel** until policy freeze and replay are real.

---

## 12. If You Started Again Today

Would I build **exactly** this? **No.**

### Better architecture (same domain knowledge)

1. **Core kernel module:** EvaluationContext, Clock, FactSnapshot, PolicyPackage (executable), MetricResult, EvidenceRef, SubjectId.  
2. **Source modules** (bureau/gst/banking/tax) producing only canonical domain events + facts.  
3. **Analytics module:** Metric DAG from registered formulas + Reconciliation engine (single path).  
4. **Policy module:** DSL/rules execute **only** from frozen package bytes.  
5. **Decision module:** recommendation assembly; workflow stays LOS.  
6. **Adapters** outside core with contract tests and golden files (Equifax, Karza, Setu, future SurePass/CIBIL).  
7. **Outbox** for ingest → normalize → metrics → recon.  
8. **Partitioned banking store** from day one.

Keep from current work: GST period model, bank taxonomy, ITR form/presumptive semantics, recon catalogue, DI discipline, shadow cutover idea.

### Migration effort (from current code)

| Workstream | Effort |
|------------|--------|
| EvaluationContext + Clock + config freeze | 3–6 weeks |
| Execute rules from `policy_content` | 4–8 weeks |
| Retire dual recon + move MetricResult to core | 2–4 weeks |
| Shared Evidence BC + fill refs | 3–5 weeks |
| Identity/subject graph MVP | 6–10 weeks |
| Banking partition/retention | 4–8 weeks |
| Provider SPI + golden corpus | 4–6 weeks |
| Kill gap defaults on policy path (flagged cutover) | 6–12 weeks product+risk |
| Live multi-source validation + ops | Ongoing 4–8 weeks |

**Rough total to “architecture-honest production authority”:** ~4–7 months with a focused team — not a rewrite from zero.

---

## 13. Production Readiness

# YES WITH CHANGES

**Not YES:** freeze/replay/tenant/gap-default/validation gaps are material for regulated lending.  
**Not NO:** the domain model and cutover strategy are salvageable and mostly right; scrap-and-replace would destroy hard-won India-source semantics.

Approve for:

- Continued **shadow / dual-write** in controlled tenants — **yes**, with observability.  
- **Authoritative production underwriting engine** — **only after** Priority Changes (§ below).

---

## Strengths

1. Correct long-term engine decomposition and ADRs.  
2. Honest missing-data posture in canonical paths.  
3. India-native source models (GST, ITR forms, AA taxonomy).  
4. Cross-source reconciliation treated as first-class (C5).  
5. Evidence strength separated from credit risk.  
6. Fail-soft hooks protect current production during migration.  
7. Substantial automated tests (~170 CI package tests).  
8. Documentation volume and intent quality above average for LOS codebases.

## Weaknesses

1. **Policy freeze without policy execution.**  
2. **Replay impurity** (clock, config, live rules, live metrics).  
3. **Silent gap defaults still authoritative** in `CreditControlService`.  
4. **Weak multi-tenancy** (default UUID, no RLS).  
5. **Dual reconciliation / metric paths.**  
6. **MetricResult / Evidence package coupling smells.**  
7. **No SurePass (or multi-bureau) presence**; Equifax/Karza still shape-coupled.  
8. **Live multi-source E2E never closed** (synthetic C5; F–C4 live outstanding).  
9. **LimitSizingService untouched** by CI.  
10. **AI gate unimplemented** relative to docs.  
11. **Orchestration still Java order** (ADR-007 unmet).  
12. Banking scale strategy incomplete.

## Technical Debt

| Debt | Severity |
|------|----------|
| Live rules vs frozen policy_content | Critical |
| Gap defaults on production path | Critical |
| Clock / config not frozen | Critical |
| Adapter live metric overlay on frozen snapshot | High |
| Dual xsrc + C5 recon | High |
| CiMetricResult in bureau package | Medium |
| Banking-only evidence groups | Medium |
| compat.* as long-term fact model | Medium |
| Default tenant UUID | High |
| JSONB unversioned shapes | Medium |
| Embedded test payloads only (no fixture corpus) | Medium |

## Scalability

- Vertical Java services OK early.  
- Bank txn volume is the existential DB risk.  
- Metric/recon append without retention will bloat.  
- 100 lenders need true tenant isolation and per-tenant policy packages — not string allowlists.

## Security

- Internal token on admin APIs — thin.  
- PAN hashing/last4 patterns started — good.  
- Raw provider payloads must not leak via ordinary roles (stated; enforce systematically).  
- No RLS; confused-deputy risk if APIs trust headers lightly.  
- Equifax credentials in SOAP build path — existing provider risk, not CI-specific.

## Performance

- Acceptable for shadow volumes.  
- Not proven for 500k-txn classification at underwrite latency.  
- Move heavy normalize/metric/recon off the synchronous sanction path.

## Maintainability

- Clear family packages help.  
- Growing God-services (`ShadowCreditEvaluationService`, snapshot builder) threaten maintainability.  
- Docs help onboarding; dual paths hurt.  
- Without executable policy and SPI contracts, every new provider is a bespoke extractor project.

## Regulator Readiness

**Directionally strong; operationally not ready.**  
Regulators will ask: *show the exact facts and rules that produced this decision six months ago.* Today you can show **tables that look like that**, but not a closed replay proof. Gap defaults are a regulatory narrative risk if ever disputed.

## AI Readiness

**Policy correct; product incomplete.** Safe to feed structured CI outputs to AI for narrative. Unsafe to cut over AI into decisioning. Implement accept/reject promotion before claiming AI-LOS integration maturity.

## Recommended Refactoring

1. Introduce **EvaluationContext** (snapshot + policyVersion + Clock + frozen config).  
2. Make **Policy Engine execute frozen content only**.  
3. Stop live metric overlays on frozen snapshots; pin metric result IDs into snapshot metadata.  
4. Unify metrics/recon into one analytics path; delete dual variance.  
5. Extract **shared kernel** (MetricResult, Evidence, Subject).  
6. Identity graph MVP for subject-safe reconciliation.  
7. Partition + archive strategy for `ci_bank_transaction`.  
8. Provider SPI + golden fixtures (Equifax, Karza, AA; add SurePass/CIBIL when real).  
9. Replace gap defaults with DI/REFER on policy path behind a cutover flag.  
10. Wire LimitSizing to versioned metrics when CI becomes authoritative.

## Priority Changes (before authoritative go-live)

| P | Change |
|---|--------|
| P0 | Frozen policy **execution** + injectable Clock + config freeze |
| P0 | Remove / quarantine production gap defaults on policy path |
| P0 | Close **live or stored multi-source** validation (GST+Bank+ITR+Bureau) with honest report |
| P1 | Snapshot-pure evaluation (no live metric overlay) |
| P1 | Tenant isolation real (no default-all-to-0001; consider RLS) |
| P1 | Banking volume strategy |
| P2 | Retire dual recon; shared Evidence BC |
| P2 | Provider SPI + fixture corpus |
| P2 | AI FACT_CANDIDATE gate |
| P3 | Declarative orchestration; LimitSizing packs; Monitoring |

## Architecture Score

| Axis | Score (0–10) |
|------|--------------|
| Target conceptual architecture | **8.5** |
| Implementation fidelity to target | **5.5** |
| Source-family domain quality | **7.5** |
| Policy / decision kernel | **4.0** |
| Replay / audit purity | **3.5** |
| Cross-source intelligence | **7.0** |
| Ops / scale readiness | **4.5** |
| **Overall architecture score** | **6.5 / 10** |

## Production Readiness Score

| Use | Score |
|-----|-------|
| Shadow dual-write in pilot tenants | **6.5 / 10** |
| Authoritative production underwriting engine | **4.0 / 10** |

---

## Closing Answers (condensed)

1. **Overall:** Fundamentally correct target; over-engineered on dual paths / JSON; under-engineered on freeze, identity, scale, SPI.  
2. **Domain:** Keep source aggregates; fix shared kernel; don’t invent new families yet.  
3. **Packages:** Survive if kernel extracted; eventual BC split optional.  
4. **DB:** Banking + append metrics are the growth bombs; tenant/RLS weak.  
5. **Performance:** Unproven at 500k txns; move off hot path.  
6. **Replay:** Not truly deterministic today.  
7. **Events:** More async for metrics/recon/shadow; keep decision sync until cutover.  
8. **Rules:** Facts → Metrics → Recons (+ evidence refs); freeze thresholds in policy.  
9. **AI:** Docs right; implement gate; don’t redesign principles.  
10. **Adapters:** Equifax/Karza workable but fragile; **SurePass absent**; need SPI + fixtures.  
11. **Vs peers:** Strong India recon story; weak decision kernel purity.  
12. **Start again:** Same domain, stricter kernel — migrate 4–7 months, don’t rewrite.  
13. **Approval:** **YES WITH CHANGES.**

---

## If this were my platform, these are the five changes I would make before going live

1. **Make evaluation pure:** `EvaluationContext = frozen snapshot + frozen policy package bytes + frozen config + injectable Clock` — and run rules/metrics/recon **only** from that. Kill live rule lookup and live metric overlays.  
2. **Kill silent gap defaults on the policy path** (or hard-gate them as DEFAULTED → cannot PASS eligibility). Regulators and your future self will thank you.  
3. **Prove one real multi-source file** (stored or anonymized Equifax + Karza GST + AA + Karza ITR) through Source → Facts → Metrics → Recon → Shadow, with an honest validation report — no synthetic theatre as “live ready.”  
4. **Fix tenancy and banking scale:** stop defaulting everything to `000…0001`; design partition/retention for bank transactions before the first large lender.  
5. **Collapse dual intelligence paths:** one MetricResult kernel, one Reconciliation engine (retire family-local `xsrc` as authority), shared Evidence refs filled for underwriter drill-down — then productize the credit-evidence API as the underwriter’s truth screen.

Until those five land, keep CI as a **shadow mirror**. Do not crown it the production underwriting engine.

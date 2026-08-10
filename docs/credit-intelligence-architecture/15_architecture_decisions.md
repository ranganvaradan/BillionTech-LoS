# 15 — Architecture Decision Records

## ADR-001 — LOS as system of record

**Context:** Two platforms (LOS + AI-LOS); risk of AI decisions becoming authoritative.  
**Decision:** BillionTechLOS is sole system of record for applications, facts, metrics, policies, decisions, sanction, monitoring actions.  
**Alternatives:** Shared SoR; AI-LOS SoR for scoring.  
**Consequences:** All binding state in LOS; AI is assistive.  
**Risks:** LOS must scale for new engines.  
**Migration:** Keep current LOS ownership; stop ignoring AI only by adding gated accept — never auto-apply AI decision.

## ADR-002 — Immutable raw sources

**Context:** Provider payloads overwritten or only kept partially in parsedData.  
**Decision:** Source artifacts are write-once; re-ingest creates new artifacts.  
**Alternatives:** Mutable overwrite; store only normalized.  
**Consequences:** Storage cost; full auditability.  
**Risks:** Retention/cost.  
**Migration:** Wrap existing providers to register artifacts.

## ADR-003 — Provider-neutral facts

**Context:** Rules keyed to provider shapes and flat scorecard keys.  
**Decision:** Rules/metrics bind only to canonical fact/metric paths.  
**Alternatives:** Keep provider JSON in rules.  
**Consequences:** Adapter layer mandatory.  
**Risks:** Mapping effort.  
**Migration:** Canonicalization phase.

## ADR-004 — Immutable fact snapshots

**Context:** No historical reproducibility (investigation Part 6).  
**Decision:** Every evaluation binds to a hashed, immutable fact snapshot.  
**Alternatives:** Live mutable application JSON.  
**Consequences:** Replay/sim/back-test possible.  
**Risks:** Snapshot size.  
**Migration:** Foundation phase dual-write.

## ADR-005 — Immutable policy packages

**Context:** Mutable rules_json/scorecards; weak scorecard version int.  
**Decision:** Published policy packages freeze rule/metric/scorecard/orchestration versions + checksum.  
**Alternatives:** Git-only; live edit.  
**Consequences:** Maker-checker publish; no silent drift.  
**Risks:** Authoring UX change.  
**Migration:** Publish-on-activate; shadow pins version.

## ADR-006 — Deterministic metric execution

**Context:** Metrics mixed into CreditControl with defaults.  
**Decision:** Versioned Metric Engine DAG; missing-data policy explicit; no silent invention.  
**Alternatives:** Keep ad-hoc Java.  
**Consequences:** Testable catalogue; selective re-eval.  
**Risks:** Formula migration.  
**Migration:** Register FOIR/LTV/limit sizing first.

## ADR-007 — Explicit orchestration

**Context:** Business precedence encoded as Java call order.  
**Decision:** Declarative stages in policy package.  
**Alternatives:** Keep FlowService order.  
**Consequences:** Configurable product pipelines.  
**Risks:** Misconfiguration.  
**Migration:** Encode current order as default stage graph.

## ADR-008 — DATA_INSUFFICIENT semantics

**Context:** Gap defaults fabricate passable numbers.  
**Decision:** Missing required inputs → DATA_INSUFFICIENT (or hard fail per policy); DEFAULTED banned on production policy path.  
**Alternatives:** Keep defaults for demo.  
**Consequences:** More REFER/DI outcomes initially.  
**Risks:** Throughput.  
**Migration:** Flag defaults in snapshot; ban in shadow then prod.

## ADR-009 — AI fact-candidate gate

**Context:** No accept path; AI decision ignored (good) but unused assistively.  
**Decision:** AI FACT_CANDIDATE requires human accept before fact snapshot inclusion.  
**Alternatives:** Auto-accept high confidence.  
**Consequences:** Safe assistive loop.  
**Risks:** Analyst load.  
**Migration:** Intelligence phase.

## ADR-010 — Human approval

**Context:** MANUAL_REVIEW exists; CAM/sanction separate.  
**Decision:** Decision Engine recommends; humans approve; AI never approves.  
**Alternatives:** Straight-through for low risk without human.  
**Consequences:** Authority matrix required for STP exceptions (business-defined).  
**Risks:** Latency.  
**Migration:** Preserve manual UW + CAM; formalize STP later if approved.

## ADR-011 — Event-driven monitoring

**Context:** No LOS monitoring; unfinished AI webhook.  
**Decision:** Monitoring Engine consumes events/schedules; actions in LOS.  
**Alternatives:** Batch-only.  
**Consequences:** EWS tasks in LOS.  
**Risks:** Alert noise.  
**Migration:** Phase M.

## ADR-012 — Shadow migration

**Context:** Pre-prod baseline but still need safe cutover.  
**Decision:** Shadow evaluation before switching decision authority.  
**Alternatives:** Big-bang replace.  
**Consequences:** Diff-driven confidence.  
**Risks:** Dual cost.  
**Migration:** Foundation first task.

## ADR-013 — No direct AI→LOS database writes

**Context:** EWS designed webhook; risk of tight coupling.  
**Decision:** Only versioned APIs/events into LOS.  
**Alternatives:** Shared DB.  
**Consequences:** Clear trust boundary.  
**Risks:** API work.  
**Migration:** Do not implement shared DB.

## ADR-014 — Evidence-linked decisions

**Context:** Free-text reasons; no evidence refs.  
**Decision:** Every rule/metric/decision result carries evidence and fact/source refs.  
**Alternatives:** Text only.  
**Consequences:** Standard result model.  
**Risks:** Storage.  
**Migration:** Standard schema in Foundation.

---

## Unresolved decisions needing business input

1. Straight-through approval without human for which segments/amounts?  
2. Retention periods by source type (bureau, AA, AI)?  
3. Authority matrix thresholds (amount × grade × deviation)?  
4. Tolerance bands for GST↔bank / ITR↔GST reconciliations?  
5. Multi-tenant vs single-lender packaging priority?  
6. Which AI providers are approved for production prompts?  
7. Bureau tradeline retention vs summary-only regulatory stance?  
8. Champion/challenger traffic split % and success metrics?

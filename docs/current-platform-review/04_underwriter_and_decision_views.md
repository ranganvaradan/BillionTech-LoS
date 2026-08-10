# 04 — Underwriter and Decision Views

---

## Credit Evidence View (what exists)

`CreditEvidenceViewBuilder` produces a deterministic map (not a polished UI). Sections:

### BORROWER / IDENTITY
Case code, data origin, non-production KYC note.

### DATA COVERAGE
Sources present (gst / itr / bank / bureau / …), coverage %.

### BUREAU
Available flag; EMI; live unsecured count (from stubs/metrics).

### BANKING
Turnover; EMI; ABB.

### GST
Turnover.

### ITR / TAX
Turnover; total income; GTI; business/profession (heads distinct).

### TURNOVER TRIANGULATION
GST / ITR / Bank values + recon refs when present.

### OBLIGATIONS
Bureau vs bank vs declared EMI; match rows.

### KEY CONFLICTS / MATERIAL RECONCILIATIONS
MATERIAL_VARIANCE / CONFLICT / DI rows only.

### DATA GAPS / DATA QUALITY
Incomplete flags (CASE_B / CASE_E style).

### EVIDENCE STRENGTH
CASE_A → STRONG / 85 (case-coded today).

### OPEN INVESTIGATION QUESTIONS
Generated from recon conflicts (deterministic).

### Optional attachments
`PolicyEngineShadow`, `CreditDecisionView`, `AiUnderwriterView` (when integrators attach).

---

## Is this usable by a real credit manager today?

**Partially as an internal/API read model — not as a finished underwriter workstation.**

### UX gaps required

1. Dedicated LOS screen wiring evidence view (flags + application id)  
2. Drill-down from recon variance → underlying periods/transactions  
3. Masked PII presentation  
4. Clear “SHADOW — not production decision” banner always on  
5. Side-by-side legacy vs canonical  
6. Action buttons for open questions (assign / resolve)  
7. Printable / CAM-linked evidence pack  
8. Performance for large tradeline sets  

---

## Policy Engine execution (shadow — illustrative)

Package: SHADOW executable DSL (`POLICY_DSL_V1`). Stages declarative (BUREAU / BANKING / …).  

Representative DigiLeap-style results (when metrics supplied):

| Rule | Input | Threshold | Result | Reason shape |
|------|-------|-----------|--------|--------------|
| Starter ADB≥EDI | ADB, PROPOSED_EDI | EDI param | PASS/FAIL/DI | BANK_* |
| DigiLeap ADB/5 | ADB/5 vs EDI | EDI | PASS/FAIL | compound |
| DigiLeap txn≥20 | txn avg | 20 | PASS/FAIL | |
| Smart Switch settlement | settlement metrics | EDI, 20 | Often DI | taxonomy |
| Inward return | count/ratio | branch @100 | Depends on boundary resolution | |
| Bureau score OR NTC | score/ntc | 650 / NTC | PASS/FAIL | knockout |
| Max DPD 6m | max_dpd | 30 | PASS/FAIL | |
| Inquiries current month | count | 3 | PASS/FAIL | frozen clock |
| CC overdue | amount | 5000 | PASS/FAIL | |
| Overdue exception | age, new credit, clean, amt | compound | PASS/FAIL/DI | CLEAN may block |

**Final policy outcome:** PASS / FAIL / REFER / DATA_INSUFFICIENT via package aggregation (knockout/hard precedence). Always `shadowOnly`.

---

## Scorecard modernization

Package-defined components reference FACT/METRIC paths with bands and `onMissing` (not silent zero).

| Question | Answer |
|----------|--------|
| Independent of flat scorecard map? | **Architecturally yes** in shadow packages |
| Wired as production scorecard? | **No** — live path still `ScorecardPolicyEngine` + bag keys |
| Ready to replace legacy eventually? | **Directionally yes**; needs certified packages, real data, cutover — **not ready now** |

---

## Decision Engine (same CASE / fixture strategy)

`P2_VALIDATION_STRATEGY_V1` (labeled **VALIDATION_FIXTURE_DECISION** — not customer policy).

Example structuring story (illustrative with strategy params):

| Dimension | Behavior | Why |
|-----------|----------|-----|
| Requested | e.g. ₹10L | Application field |
| Turnover limit | % of triangulated turnover | Strategy `turnoverPct` 20%, source TRIANGULATED |
| Banking limit | multiplier × ADB | e.g. 3× |
| FOIR limit | EMI capacity from income × allowed FOIR | Missing income → DI |
| DSCR | cashflow / min DSCR | Missing CF → DI |
| Collateral | LTV/haircut | Optional |
| Policy cap | e.g. ₹9L | Parameter |
| Combine | **MIN** of eligible | Explicit — not silent max |
| Tenure | Clamp to 6–36, default 18 | Deterministic |
| Pricing | Base 12.5% + grade/unsecured/tenure bps | Floor/cap; weak evidence → REFER preference |
| Conditions | Auto from DI / recon / collateral shortfall | Evidence-linked |
| Authority | Amount × grade bands | Matrix-driven |
| Outcome | APPROVE / COUNTER_OFFER / REFER / DI / DECLINE | `authoritative=false` |

### Is this genuine credit structuring or a wrapper?

**Genuine multi-method structuring engine exists** (limits, FOIR amortization, pricing components, authority).  
**But:** pilot strategy is a **validation fixture**; hybrid cutover still leaves LIMIT/PRICING/AUTHORITY as LEGACY for production; not yet lender-certified. Treat demos as **capability proof**, not live credit policy.

---

## AI Underwriter (assistive)

Outputs: NARRATIVE, EXPLANATION, QUESTION, CAM_DRAFT, ALTERNATE_STRUCTURE_SUGGESTION, …  
All: `AI_SUGGESTION`, `authoritative=false`, `humanReviewRequired=true`.  
Grounding rejects invented metrics/rule IDs/mismatched amounts.  
Scenario amounts computed by **Decision Engine**, not the LLM.  
Outage → `AI_ASSISTANCE_UNAVAILABLE` — underwriting continues.

Example rejected grounding: text claiming `gst.turnover.trailing_12m = 999999999` when evidence says 84,000,000 → `REJECTED_GROUNDING_FAILURE`.

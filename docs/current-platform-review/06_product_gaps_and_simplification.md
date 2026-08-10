# 06 — Product Gaps and Simplification

---

## Architecture complexity verdict

**We are building a real Credit Intelligence Platform spine — and we have accumulated more layers than a V1 product needs on screen.**

### Genuinely useful

- Immutable EvaluationContext + frozen clock/config  
- Explicit DATA_INSUFFICIENT (vs silent zeros)  
- Cross-source reconciliation catalogue  
- Policy Studio → immutable SHADOW package → one DSL interpreter  
- Multi-method Decision Engine with explainable limits  
- AI grounding + non-authority  
- Cutover quarantine + dual-run + kill switch design  

### Possibly unnecessary / heavy for now

- Many parallel admin surfaces before one underwriter UX  
- Dual recon paths historically (family xsrc metrics + C5 wraps)  
- Scorecard bag bridge (`compat.*`) lasting too long  
- Overlapping readiness scorers (C6 assessor + G0 + G0.1) without one executive dashboard  
- In-memory stores in tests vs incomplete persistence on some simulate paths  

### Duplicate concepts to merge eventually

| Concept A | Concept B |
|-----------|-----------|
| `CiPolicyPackage` / version (V86) | `CiExecutablePolicyPackage` (V103) |
| C6 DualPolicyEvaluator | P1 LegacyVsDsl + G0 dual-run |
| Evidence strength case grades | Broader recon evidence metrics |
| Policy Studio simulation | Policy Engine simulate |

### Compatibility that must eventually die

- `CreditControlService` SCF_GAP / GAP / DEMO numeric fillers on authoritative path  
- Flat scorecard map as true underwriting bus  
- AI-LOS deep-link DEMO `TEST001` fallback on legacy open path  
- Live DB rule sets while claiming frozen policy purity  

### Verdict

**Right architectural path; too many unfinished product surfaces.** Prefer consolidating UX and real-data proof over adding engines.

---

## Five differentiating capabilities (implemented, tangible)

1. **Evidence-linked multi-source underwriting read model** (facts + recon + questions)  
2. **Cross-source turnover/obligation triangulation with material variance honesty**  
3. **Policy document → ambiguity-gated → executable DSL package** (Banking/Bureau BRE)  
4. **Deterministic frozen evaluation + replay hashes**  
5. **Explainable multi-method facility structuring + grounded AI suggestions that cannot mutate credit**  

---

## Before writing more code — change these

1. **Stop net-new engines.** Enter real/stored multi-source validation (anonymized).  
2. **Build one underwriter Evidence + Dual-run screen** on existing APIs.  
3. **Quarantine or kill silent gap defaults** for any cohort you care about.  
4. **Close or scope-out Policy Studio ambiguities** for the pilot product.  
5. **Simplify package taxonomy** (one executable package story).  
6. **Retire DEMO URL from any path that looks like success.**  

---

## Should V1 real-data validation begin after this review?

**Yes.** G0.1 already blocked LIMITED_PILOT_READY solely on evidence. Architecture is ahead of data. Real/stored validation is the correct next investment — not another phase of greenfield engines.

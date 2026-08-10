# 07 — CEO Demo Script (15 minutes)

**Reality check:** With flags default **OFF**, a stock LOS login shows **legacy underwriting**.  
This script assumes a **prepared demo environment**: CI flags on for a sandbox tenant, fixtures loaded, internal APIs or a thin demo UI available. Steps marked **BACKEND-ONLY** need engineer-assisted screens today.

| # | Minute | Step | Backend support today? |
|---|--------|------|-------------------------|
| 1 | 0:00 | Open application (sandbox) | **Partial** — LOS app yes; CI overlay needs wiring |
| 2 | 1:00 | Show data sources present (bureau/GST/bank/ITR) | **Yes** — evidence DataCoverage / harness |
| 3 | 2:30 | Show turnover triangulation GST/ITR/Bank | **Yes** — evidence + recon (CASE_A) |
| 4 | 4:00 | Flip to CASE_C or show material variance | **Yes** — CASE_C fixture |
| 5 | 5:30 | Open uploaded Banking BRE (text/PDF fixture) | **Yes** — Policy Studio documents |
| 6 | 7:00 | Show AI/deterministic interpretation + ambiguities | **Yes** — studio APIs; UI thin |
| 7 | 8:30 | Resolve one ambiguity (e.g. PROPOSED_EDI) | **Yes** — resolve API; **BACKEND-ONLY** UX |
| 8 | 9:30 | Show executable DigiLeap rule + tests | **Yes** — draft package / preview |
| 9 | 10:30 | Run shadow Policy Engine | **Yes** — policy-engine simulate |
| 10 | 11:30 | Show Decision Engine recommended facility | **Yes** — decision simulate (fixture strategy) |
| 11 | 12:30 | Show AI CAM draft / summary | **Yes** — stub provider; grounded |
| 12 | 13:30 | Replay / same hash after “live config change” story | **Yes** — tests/APIs; **BACKEND-ONLY** narrative |
| 13 | 14:30 | Close: legacy still authoritative; cutover NOT_READY | **Honest** — required |

### Do not claim in the demo

- Production decisions come from AI or CI  
- LIMITED_PILOT_READY / G1 readiness  
- Real customer portfolio dual-run statistics  
- Credit Head self-serve Policy Studio without assistance  
- Silent gap defaults are gone  

### Best supporting fixtures

- Evidence: **CASE_A_STRONG** then **CASE_C** conflict  
- Policy: Banking BRE + Bureau BRE golden text  
- Decision: `P2_VALIDATION_STRATEGY_V1` labeled validation fixture  
- Cutover teaching: **CASE_B** legacy-default-dependent  

---

## One-sentence demo close

> “We can show evidence, policy authoring, deterministic shadow decisions, and grounded AI assist — while production credit still runs on the legacy engine until real data and gap-default elimination clear cutover gates.”

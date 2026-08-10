# 05 — Legacy vs Canonical + Replay + Reality Table

---

## Side-by-side (CASE_A style)

| Input | LEGACY (scorecard / gaps) | CANONICAL (CI) |
|-------|---------------------------|----------------|
| Live unsecured | Stub **1** (or gap **2** if missing) | `bureau.live_unsecured_count` — DI if bureau absent |
| GST turnover | Stub **8.4 Cr** or SCF gap **5.2 Cr** | `gst.turnover.trailing_12m` |
| ABB | Stub **1.85 L** or gap **1.2 L** | `bank.abb.average` / ADB 3m |
| Bank turnover | Stub **7.7 Cr** or SCF gap **4.1 Cr** | `bank.turnover.trailing_12m` |
| EMI | Stub **95k** or gap **15k** | Bureau/bank EMI + recon |
| ITR income | Stub **42 L** or SCF gap **4.5 L** | Distinct ITR heads |
| Policy outcome | Live rule engine | Shadow DSL package |
| Score | Flat scorecard bag | Package scorecard components (shadow) |
| Recommendation | Limit sizing + CAM | Shadow Decision Engine (`authoritative=false`) |

**Flags that mark legacy pollution:** `PROVIDER_GAP_DEFAULT_ACTIVE`, `DEMO_FALLBACK_ACTIVE`.

**CASE_B** is the teaching case: legacy looks “fine” because gaps fill; canonical returns **DATA_INSUFFICIENT** / stricter without inventing numbers.

---

## Deterministic replay (C5.1 / P1 / P2)

Contract:

```text
EvaluationContext (snapshot + policy/package + config freeze + clock + metric/recon sets)
→ evaluate → deterministicHash
→ mutate live YAML / wall clock / newer metrics / live DB rules
→ replay from frozen context
→ same hash for decision-critical results
```

| Mutation | Should affect replay? |
|----------|------------------------|
| Live policy YAML change | No |
| System clock / `LocalDate.now()` | No (FixedEvaluationClock) |
| Append newer metric outside pinned set | No |
| Live application field edit | No if not in frozen snapshot |
| Change frozen package content | Yes — new version / new hash |

**Demo caveat:** purity is proven in **tests**; live UI “replay button” is internal API / harness, not a Credit Head screen.

---

## Capability honesty table

| Capability | Real | Fixture | Shadow | Production | Missing |
|------------|------|---------|--------|------------|---------|
| Equifax / SurePass bureau canonicalize | Partial adapters | Yes (minimal) | Yes | No | Broad live E2E |
| SurePass GST / BSA / ITR | Partial | Yes | Yes | No | Full period coverage |
| Bank AA (Setu) | Stub/synthetic | Yes | Yes | No | Live AA volume |
| Reconciliation | Engine + catalogue | C6 cases | Yes | No | Portfolio ops |
| Policy Studio | Backend APIs | Golden BRE PDFs/text | Authoring | No | Credit Head UI |
| Policy Engine | DSL interpreter | Golden packages | **Only** | No | ACTIVE forbid |
| Decision Engine | Limit/pricing/etc. | Validation strategy | **Only** | No | Lender strategies |
| AI Underwriter | Stub (+ optional HTTP) | Grounded narratives | Assistive | No | Live model ops |
| Dual-run cutover | Framework | Fixture stats | Prep | Legacy still authority | Real/stored ≥20 |
| Limited pilot cert | Gates coded | — | — | **NOT_READY** | Real data |
| CAM / sanction | Legacy | — | Adapter read-only | **Yes (legacy)** | Canonical write |
| Silent gap defaults | — | CASE_B | Quarantine opt-in | **Still live** | Elimination |

---

## Cutover snapshot (G0.1)

- Candidate ranked: **DIGILEAP** (objective, not assumed)  
- `realStoredCaseCount = 0`  
- `LIMITED_PILOT_READY = false`  
- `allow-canonical-authority = false`  
- G1 must **not** begin

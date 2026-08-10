# 02 — End-to-End Case Walkthrough (CASE_A_STRONG)

**Fixture:** `los-core-service/src/test/resources/validation-bundles/case_a_strong/bundle.json`  
**Origin:** `REPRESENTATIVE_PROVIDER_FIXTURE` (composed SurePass samples + metric stubs — **not** a live portfolio loan)

This is the strongest multi-source harness case: aligned turnovers, bureau EMI ≈ bank EMI.

---

## 1. Sources

| Source | Provider fixture | Status in harness | Period (intended) | Freshness | Quality note |
|--------|------------------|-------------------|-------------------|-----------|--------------|
| GST | SurePass GST monthly minimal | Present | Trailing ~12m | Fixture | Stub turnover used for recon |
| ITR | SurePass ITR heads distinct | Present | FY stub | Fixture | Heads kept distinct (no fan-out) |
| Banking / BSA | SurePass BSA minimal | Present | Trailing 12m / 3m ABB | Fixture | AA/BSA classification limited |
| Bureau | SurePass CIBIL minimal | Present | Report as-of fixture | Fixture | Canonical statuses via normalizer |
| TIS | SurePass TIS amounts | Present | Stub | Fixture | Distinct amounts |

**Honest label:** representative fixture — safe to demo architecture; **not** proof of production data quality.

---

## 2. Important canonical facts / metric stubs (selected)

| Path / concept | Value (CASE_A) | Notes |
|----------------|----------------|-------|
| `gst.turnover.trailing_12m` | ₹8.40 Cr | Stub |
| `itr.turnover.trailing_12m` | ₹8.10 Cr | Stub |
| `bank.turnover.trailing_12m` | ₹7.70 Cr | Stub |
| `bank.abb.average` | ₹1.85 L | Stub ABB |
| `bureau.emi.monthly` | ₹95,000 | Stub |
| `bank.emi.monthly` | ₹94,000 | Stub (~aligned) |
| `bureau.live_unsecured_count` | 1 | Stub |
| `itr.income.total` | ₹42 L | Stub |
| `itr.income.gti` | ₹50 L | Distinct head |
| `itr.income.business_profession` | ₹50 L | Distinct head |
| Legacy scorecard `OBLIGATION_RATIO` | 22 | Stub |
| Legacy `ANNUAL_GST_TURNOVER` | 84,000,000 | Mirrors GST stub |

*(Full DB fact dump omitted — these are the decision-critical stubs the harness uses.)*

---

## 3. Metrics — how they appear

In the C6 harness, “metrics” are primarily **pinned stubs** on the bundle, not a live DAG recompute for every CASE_A run. Production-shadow metric services exist per family (bureau/GST/banking/tax) and write `CiMetricResult` when flags are on.

**FOIR / LTV (P1 registry stubs)** compute from frozen input maps when Decision Engine / registry is exercised separately.

---

## 4. Reconciliations (illustrative CASE_A)

Using CASE_A stubs (aligned case):

| Reconciliation | Left | Right | Variance (approx) | Expected outcome class |
|----------------|------|-------|-------------------|------------------------|
| GST ↔ ITR turnover | 8.40 Cr | 8.10 Cr | ~3.6% | Typically within warning band (fixture-aligned) |
| GST ↔ Bank | 8.40 Cr | 7.70 Cr | ~8.3% | May warn depending on tolerance |
| ITR ↔ Bank | 8.10 Cr | 7.70 Cr | ~4.9% | Mild |
| Bureau ↔ Bank EMI | 95k | 94k | ~1% | Match / non-material |
| Turnover triangulation | GST/ITR/Bank | — | Low conflict | Strong case — not CASE_C |

**CASE_C** (separate fixture) forces material GST/Bank/ITR conflict → REFER-style dual-policy finding.

Tolerances (config defaults): turnover match 1% / warn 5% / material 15%; obligation match 2% / warn 5% / material 20%.

---

## 5. Evidence strength (as coded today)

`CreditEvidenceViewBuilder` assigns by **case code**, not a deep ML model:

| Case | Grade | Score |
|------|-------|-------|
| CASE_A_STRONG | **STRONG** | **85** |
| CASE_C / CASE_D | MODERATE | 55 |
| Else (B, E, …) | WEAK | 30 |

---

## 6. What an underwriter would see (narrative)

Borrower looks multi-source complete. Turnovers roughly line up (GST slightly above bank). Obligations nearly match across bureau and bank. Evidence grade STRONG (85). Open questions would be light on CASE_A; heavier on conflict cases.

---

## Caveat

Opening “an application” in the live UI today still shows **legacy underwriting**. The CASE_A walkthrough is what the **validation / internal CI APIs and tests** contain when Credit Intelligence flags are exercised — not what a production underwriter sees by default with flags off.

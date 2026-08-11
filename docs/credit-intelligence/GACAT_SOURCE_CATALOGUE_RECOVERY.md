# GACAT-SOURCE-CATALOGUE-RECOVERY-1

Recovered underwriting source inventory into the existing **CanonicalParameterRegistry** read model (Admin → Data & Parameters + Policy Parameter Resolver).

**Not** a new catalogue DB, metric engine, or rule engine.  
`allowCanonicalAuthority=false` remains hard.

Baseline before expansion: **28** registry entries.  
After expansion: **162** registry entries.

---

## 1. Prior work / source artifacts recovered

| Area | Evidence |
|------|----------|
| Equifax retail XML | `EquifaxBureauAccountExtractor`, `equifax_accounts_minimal.xml`, simulated InquiryResponse sample |
| Tradeline model | `CiBureauTradeline`, `CiBureauPaymentHistory` |
| Commercial sample | SurePass `commercial_minimal.json` (`CommercialBureauResponseDetails`) — **no CCRResponse type in repo** |
| Bureau metrics | `BureauMetricService` (12m/24m DPD, live unsecured, exposures), `PolicyBureauMetricService.maxDpd6m` |
| Taxonomy docs | `07_bureau_taxonomy_and_metrics.md`, `06_bureau_canonicalization.md` |
| Banking | BSA fixtures, `16_banking_metrics_and_obligations.md`, banking metric services |
| AA | `AccountAggregatorService`, Setu fixture — transport/consent |
| GST | `GstMetricService` codes + fixtures |
| ITR / TIS | SurePass/Karza ITR + TIS fixtures, `TaxMetricService` |
| KYC | `KycFactCatalog` (18 facts; PEP/sanctions unsupported) |
| Parallel registries | `PolicyAuthoringRegistry`, `CreditCapabilityDefinitions`, Flyway V91/V102 seeds |

No `legacy-reference/` folder found under BillionTechLoS.

---

## 2. Source-by-source counts (registry read model)

| Source | Raw | Derived | Manual | Live (productionReady) | Total |
|--------|-----|---------|--------|------------------------|-------|
| Application | 0 | 0 | 9 | 9 | 9 |
| Bureau Retail | 33 | 25 | 0 | 34 | 58 |
| Bureau Commercial | 16 | 3 | 0 | 0 | 19 |
| Bank Statement | 10 | 12 | 0 | 9 | 22 |
| Account Aggregator | 4 | 0 | 0 | 2 | 4 |
| GST | 6 | 7 | 0 | 12 | 13 |
| Financial Statements | 7 | 5 | 0 | 0 | 12 |
| KYC | 20 | 1 | 0 | 18 | 21 |
| Program / Product | 0 | 0 | 3 | 3 | 3 |
| Customer / Borrower | 0 | 0 | 0 | 0 | 0 |
| Manual Input | 0 | 0 | 0 | 0 | 0 |
| Computed / Derived | 0 | 1 | 0 | 1 | 1 |

**Totals:** registry **162** · raw **96** · derived **54** · manual **12** · implemented **115** · productionReady **88**.

---

## 3. Capability status model

Every entry exposes:

- `sourceAvailable`
- `normalized`
- `derivationDefined`
- `implemented`
- `productionReady`
- `primaryStatus` ∈ SOURCE_AVAILABLE | NORMALIZED | DERIVATION_DEFINED | IMPLEMENTED | PRODUCTION_READY

Registry inclusion **does not** imply production-ready.

---

## 4. Max DPD 6m — exact lineage (from code)

**Canonical id:** `bureau.max_dpd_6m`

**Implementation binding (Advanced):** `PolicyBureauMetricService.maxDpd6m` (studio).  
Production `BureauMetricService` computes **`bureau.max_dpd_12m` / `bureau.max_dpd_24m` only** → `productionReady=false` for 6m.

**Business algorithm (How calculated):**

1. **Raw inputs:** per-tradeline payment-history months with numeric DPD (`bureau.tradeline.payment_history` / monthly DPD).
2. **Filters:** any tradeline with non-empty payment history. Studio helper does **not** exclude credit cards, closed, settled, or write-off accounts when history exists.
3. **Transformation:** each history month contributes its numeric DPD.
4. **Aggregation:** **MAX**.
5. **Window:** month end-date in `[asOf − 6 months, asOf]`.
6. **Missing data:** if no tradeline has payment history → **DATA_INSUFFICIENT** (not zero).
7. **STD / SMA / XXX:** not special-cased inside `maxDpd6m`; unknown months that never become `PaymentMonth` rows are skipped upstream.

---

## 5. Sample other lineage walkthroughs

| Parameter | Raw inputs | Notes |
|-----------|------------|-------|
| `banking.avg_daily_balance_3m` | EOD / closing balances + transactions | EOD carry-forward average; production banking path |
| `gst.turnover.trailing_12m` | GST period financials | `GstMetricService.computeTrailingTurnover` |
| `bureau.live_unsecured_loan_count` | account status, secured flag, balance | LIVE definition V1; production ready |
| `bureau.commercial.total_facility_exposure` | facility outstanding | **DEFINED_NOT_IMPLEMENTED** |

---

## 6. Registry gap analysis

| | Before | After |
|--|--------|-------|
| Registry entries | 28 | 162 |
| Bureau (combined) | 12 | Retail 58 + Commercial 19 |
| Bank Statement | 12 | 22 |
| GST | 1 | 13 |
| KYC | 1 | 21 |
| Financial Statements | 0 | 12 |
| Application / Program | 1 | 12 |

**Still thin / missing specs:**

- Full Equifax commercial CCR / richer facility, guarantee, enquiry trees beyond SurePass minimal fixture
- Invoice-level GSTR-1 / e-invoice field inventory
- Complete AA FI JSON schemas per FIP
- Customer / Borrower family still empty (intake often overlaps Application)
- Many commercial / ITR ratio metrics marked DEFINED_NOT_IMPLEMENTED

---

## 7. Scorecard mapping

Existing live scorecard vocab still resolves where `liveScorecardParameter` is set (e.g. `BUREAU_SCORE`, `AVERAGE_BANK_BALANCE`, `GST_INCOME`, `KYC_QUALITY`, `OBLIGATION_RATIO`, `REPAYMENT_HISTORY`).  
Unmapped scorecard factors remain those without a `liveScorecardParameter` binding — no scorecard redesign in this task.

---

## 8. Unresolved assumptions

1. Commercial bureau UW path is **not** production-ready (docs + liveCount=0).
2. Studio banking helpers ≠ automatic production-ready unless a confirmed production calculator exists.
3. AA is consent/transport; banking/GST facts remain attributed to underlying FI/source families.
4. No DB migration — all expansion is in-memory registry seed.

---

## 9. Files

- `CanonicalParameterDefinition.java` — capability status model
- `GacatCatalogueSeed.java` — recovered inventory seed
- `CanonicalParameterRegistry.java` — uses seed; Bureau Retail/Commercial sources
- `DataParametersAdminService.java` — counts + lineage enrich
- `DataParametersPage.tsx` — Raw/Derived/Live cards + How calculated
- `GacatSourceCatalogueRecovery1Test.java`
- this document

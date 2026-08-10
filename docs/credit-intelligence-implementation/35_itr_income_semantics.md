# 35 — ITR Income Semantics

## Correction

`totalRevenue` (P&L) maps to **salesTurnover / gross receipts** (business financials).

It must **not** be copied into:

- `businessProfessionIncome`
- `grossTotalIncome`
- `totalIncome`

Those income heads are set only when present as **explicit** fields on the payload.

## Why

Collapsing revenue into GTI / total income / business-profession income falsifies tax semantics and breaks cross-source reconciliation (GST/ITR/bank turnover vs taxable income).

## SurePass / Karza

- SurePass ITR adapter keeps `GrossTotalIncome`, `TotalIncome`, `total_revenue_from_operations` distinct.
- Karza extractor: `KarzaItrCanonicalExtractor` + `ItrIncomeSemanticsTest`.

## Tests

`ItrIncomeSemanticsTest`, `SurePassItrAdapterFixtureTest`.

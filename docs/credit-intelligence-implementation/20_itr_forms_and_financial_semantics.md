# 20 — ITR Forms and Financial Semantics

## Forms

| Form | Typical use | Balance sheet | Presumptive |
|------|-------------|---------------|-------------|
| ITR-1 | Salaried | Rare | No |
| ITR-2 | Capital gains / multi-house | Limited | No |
| ITR-3 | Business (non-presumptive) | Yes | No |
| ITR-4 | Presumptive 44AD/ADA/AE | Usually no | Yes |
| ITR-5 | Firms / LLP | Yes | No |
| ITR-6 | Companies | Yes | No |
| ITR-7 | Trusts etc. | Varies | No |

Normalization: `ItrFormNormalizer` maps `ITR-6` / `ITR6` → `ITR_6`.

## FY ↔ AY

- FY2024-25 = Apr 2024 – Mar 2025
- AY2025-26 assesses FY2024-25 income
- `TaxYearUtils` validates pairs and builds trailing year windows

## Effective return selection (`ITR_EFFECTIVE_RETURN_SELECTION_V1`)

Prefer latest **revised/updated** over original for the same AY. Lineage stored in `ci_tax_return_revision`.

## Field mapping (Karza return-forms)

| Canonical | Karza source |
|-----------|--------------|
| sales_turnover | profitAndLoss.totalRevenue |
| pat | profitAndLoss.profitAfterTax |
| ebitda | profitAndLoss.ebitda |
| finance_cost | profitAndLoss.interestExpense |
| total_liabilities (TOL) | balanceSheet.totalLiability |
| net_worth (TNW) | balanceSheet.totalEquity |
| business / gross / total income | derived from totalRevenue when detail heads absent; salary etc. stay null |

## Presumptive

ITR-4 or section hints (44AD/ADA/AE) → `ci_itr_presumptive_income`. Profitability and BS ratio metrics/rules return `NOT_APPLICABLE`.

## Freshness (`ITR_FRESHNESS_POLICY_V1`)

Calendar-aware: latest completed FY ends 31 Mar; non-audit due ~31 Jul of AY start (+ grace). Before due date, expected latest filed AY is the prior year.

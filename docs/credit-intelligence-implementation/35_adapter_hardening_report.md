# 35 — Adapter Hardening Report

## SurePass
- BSA: account hash/last4 only; empty/null/zero credit-debit; analysis_result → PROVIDER_OBSERVATION
- GST: monthly_data preferred for turnover; analysis_summary/dashboard as observations
- ITR: GrossTotalIncome / TotalIncome / revenue_from_operations distinct; tds_26as → FORM_26AS source metadata
- CIBIL: consumer tradelines/enquiries/scores
- Commercial: separate adapter (not consumer CIR reuse)
- TIS: reported_by_source / processed_by_system / accepted_by_taxpayer kept distinct

## Equifax / Karza / Setu
SPI wrappers around existing extractors; golden fixtures under provider-fixtures/. Flag-gated; not production ingestion authority.

# 09 — Monitoring Engine

## Purpose

Post-disbursement / post-sanction continuous credit intelligence using the **same** Source → Fact → Metric → Policy patterns.

## Inputs

Recurring AA · bank statements · GST · bureau refresh · LMS repayment · invoices · ERP · covenant submissions · collateral reports · AI anomaly suggestions  

## Modes

- Scheduled monitoring cases  
- Event-triggered (payment miss, filing delay, DPD)  
- Early-warning indicators with severity  
- Task generation & escalation in LOS  
- Limit reduction / covenant breach / renewal recommendations (**human-approved**)  

## Example EWS rules

Banking credits decline · GST filings delayed · bureau DPD↑ · cheque returns↑ · util >90% sustained · customer concentration↑ · collections slow · cash withdrawals↑ · tax arrears · related-party transfers↑ · collateral cover↓  

## Observed

- No LOS monitoring engine  
- AI-LOS EWS alerter posts to missing `/tasks/create`  

## Target

Monitoring policies live in policy packages (`stage=MONITORING`). Signals create `early_warning_signal` + LOS tasks; actions (limit change, recall) go through Decision/override authority — not AI auto-action.

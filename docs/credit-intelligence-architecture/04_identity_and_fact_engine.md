# 04 — Identity and Fact Engine

## Identity & entity resolution

### Entity types

borrower · individual · proprietorship · company · LLP · partnership · promoter · director · partner · guarantor · beneficial owner · group company · customer · supplier · lender · bank_account · gst_registration · bureau_subject

### Identifiers & matching

| Signal | Match mode |
|---|---|
| PAN, GSTIN, CIN, LLPIN | Exact |
| Aadhaar token/hash | Exact (tokenized) |
| Mobile, email, bank account | Exact / normalized |
| Name, address | Fuzzy + human confirm |
| Director relationships | Graph |

Outcomes: MATCH · POSSIBLE_MATCH · CONFLICT · MERGE_CANDIDATE — with confidence and mandatory human confirmation above thresholds.

Support: merged entities, duplicates, historical identity changes, audit of merges.

---

## Fact Engine

### Fact record

```text
UnderwritingFact {
  fact_id, canonical_path, subject_entity_id,
  value, value_type, period_start, period_end, as_of,
  classification, source_refs[], confidence, quality_status,
  normalizer_version, derivation_ref?, created_at,
  fact_set_id
}
```

### Classifications

```text
VERIFIED | DECLARED | MANUAL | EXTRACTED | AI_CANDIDATE | AI_ACCEPTED
DERIVED | RECONCILED | OVERRIDDEN | DEFAULTED | DISPUTED | STALE
```

### Allowed use by classification

| Use | Allowed classifications |
|---|---|
| Hard eligibility | VERIFIED, RECONCILED, AI_ACCEPTED (if policy allows), OVERRIDDEN (audited) |
| Scoring | Above + EXTRACTED/MANUAL if policy allows; **not** AI_CANDIDATE, DEFAULTED*, DISPUTED |
| Limit sizing | Same as scoring; prefer RECONCILED turnover |
| Pricing | Policy-defined; exclude DISPUTED/STALE |
| Sanction terms | VERIFIED / OVERRIDDEN / AI_ACCEPTED only for binding terms |
| Monitoring | Fresh VERIFIED/EXTRACTED; STALE triggers refresh |

\*DEFAULTED forbidden on production policy path; may appear only in transitional shadow flags.

**AI_CANDIDATE never authoritative without explicit acceptance → AI_ACCEPTED.**

### Fact dictionary & namespace

Example canonical paths:

```text
applicant.identity.pan
applicant.demographics.age_years
related_party.guarantor[0].pan
banking.account[iban].avg_balance_3m
banking.emi_bounce_count_12m
bureau.consumer.score
bureau.tradeline[*].dpd_max_24m
bureau.live_unsecured_count          # derived metric input from tradelines
itr.ay{year}.gross_total_income
gst.fy{year}.taxable_turnover
financials.fy{year}.ebitda
obligations.monthly_emi_total
collateral.property[0].market_value
invoice.receivable.overdue_gt_90_ratio
tax.form26as.tds_total
fraud.device.risk_score
monitoring.aa.credits_mom_change
```

### Snapshot

```text
FactSnapshot {
  snapshot_id, application_id, created_at, hash,
  fact_ids[], provenance_map, immutable=true
}
```

Supersession: new facts reference `supersedes_fact_id`. Conflicts raise DISPUTED until resolved. Snapshots never mutate.

### Observed → target

| Current | Target |
|---|---|
| `ctx.scorecard` BigDecimal bag | Facts + metrics with classification |
| Manual cells in financialInfo | MANUAL facts |
| Gap defaults | Explicit DEFAULTED or DATA_INSUFFICIENT — not silent pass |
| Bureau aggregates only | Tradeline facts → metrics |

# 21 — AIS / Form 26AS Reconciliation

## Status

AIS and Form 26AS are **future-ready**: V94 tables, fact definitions (V95), extractor hooks, metrics, and admin APIs exist. Production Karza ITR return-forms payloads typically omit these nodes today.

## Extraction

`KarzaItrCanonicalExtractor` looks for:

- `ais` / `AIS` / `annualInformationStatement`
- `form26as` / `form26AS` / `twentySixAS` / `form26As`

If absent → `aisAvailable=false` / `form26AsAvailable=false` (no invent).

## Metrics

| Metric | Behavior when source missing |
|--------|------------------------------|
| `xsrc.itr_26as_tds_variance` | `DATA_INSUFFICIENT` |
| `xsrc.itr_ais_income_variance` | `DATA_INSUFFICIENT` |

When both sides present, outcomes use config warning/material pcts:

- `MATCH` / `ACCEPTABLE_VARIANCE` / `MATERIAL_VARIANCE` / `CONFLICT`

## Source registry

Separate source records with `SourceType.AIS` / `FORM_26AS` (or linked from ITR pull) with artifact ref to the same `kyc_step_result:{id}`.

## Shadow rules

`XSRC_ITR_26AS_TDS_VARIANCE` and `XSRC_ITR_AIS_INCOME_VARIANCE` map metric variance outcomes to PASS/FAIL/REFER/DI.

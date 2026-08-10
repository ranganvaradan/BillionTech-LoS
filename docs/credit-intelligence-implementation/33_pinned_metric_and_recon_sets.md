# 33 — Pinned Metric and Reconciliation Result Sets

## Metric result sets

`MetricResultSetService` pins ordered metric result IDs (+ codes) into `ci_metric_result_set` with a content hash.

`PinnedMetricLookup`:

- when EvaluationContext has `metricResultSetId` → resolve from the pinned set only
- otherwise (transitional) → latest-by-`createdAt` on `ci_metric_result`

Appending a newer metric row for the same code must **not** change pinned lookup.

## Reconciliation result sets

`ReconciliationResultSetService` mirrors the same pin pattern for `ci_reconciliation_result` IDs (see V98).

## Tests

`PinnedMetricLookupTest`.

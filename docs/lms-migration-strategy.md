# LMS migration strategy (Phase 3)

## Goals

- Reach **functional parity** with bl-core Encore usage for disbursement handover, repayment posting, summaries, and schedules **without** redesigning the LOS workflow engine.
- Decouple **display / catalog** `loan_product` from Encore **`productCode`** using database-driven mapping.

## Sequences (target)

1. **Sanction / KFS** — unchanged; continues to use application and CAM data. Optional future: pull Encore pre-open summary if product policy requires vendor validation before sanction.
2. **Disburse (workflow step)** — `DisburseLmsStepExecutor` builds `LoanHandoverRequest` with `loanProduct` = application label/code and `productCode` = resolver output → `LmsService.handoverLoan` → `EncoreLmsApi.openLoanAccount` + `disburse`.
3. **Servicing** — repayment callbacks and prepayment call `EncoreLmsApi.repay`; summaries refreshed on read and via `EncoreSummarySyncJob` when `los.lms.encore.sync.enabled=true`.

## DTO mapping (bl-core ↔ los-core)

| bl-core | los-core |
|---------|----------|
| `LoanDto` + `LoanProductDto.productCode` | `LoanHandoverRequest.productCode` |
| `LoanDto.amountRequired` / interest / tenure | `LoanHandoverRequest.sanctionedAmount`, `interestRate`, `tenureMonths` |
| `EncoreFindSummariesDto.principalDue` (from JSON `accountBalance`) | `LmsAccountSummary.outstandingPrincipal` via `EncoreSummaryNormalizer` |
| `EncoreFindSummariesDto.daysPastDue` | `LmsAccountSummary.dpd` |

## Entity and schema impact

| Artifact | Purpose |
|----------|---------|
| `workflow_lms_product_mapping` (Flyway V45) | Stores Encore `productCode` per `(borrower_type, loan_product)`. |
| `loan_applications.lms_encore_customer_id` (V45) | Reserved nullable column for idempotent Encore customer create when implemented. |
| Existing `lms_loan_handover`, `lms_account_summary`, `lms_repayment_callback` | Continue to store handover and sync state (V41+). |

No change to `workflow_configs.steps` JSON shape.

## Feature flags and rollout

| Property | Default | Effect |
|----------|---------|--------|
| `los.lms.workflow-mapping.enabled` | `false` | When `false`, `WorkflowLmsProductResolver` returns the fallback (`loan_product` string), identical to pre-mapping behaviour. |
| `los.lms.encore.sync.enabled` | `false` | Enables nightly `EncoreSummarySyncJob`. |

**Rollout:** enable `workflow-mapping` in lower environments after validating seeded `encore_product_code` values against the real Encore catalogue; then production with monitoring on `ENCORE_ERROR` handover status in `lms_loan_handover`.

## Rollback

- **Application:** set `los.lms.workflow-mapping.enabled=false`.
- **Database:** mapping table is additive; optional `DELETE FROM workflow_lms_product_mapping` or new Flyway script to truncate if a bad seed shipped (prefer `UPDATE` rows to corrected codes).

## Gap list (tracked work)

1. **`findSummary` JSON** — not exposed through `LmsService`; add if a consumer needs full JSON parity with `RetailController`.
2. **LMS customer create** — deferred; see [`lms-customer-onboarding-deferred.md`](lms-customer-onboarding-deferred.md).
3. **KFS numeric parity** — ensure sanction/KFS templates use the same EMI basis as Encore when `getRepaymentSchedule` reads vendor schedule; document field-level diff per product.
4. **Fees / total demand** — `EncoreSummaryNormalizer` does not yet aggregate `fees` array like bl-core `toFindSummariesDto`; extend when collections UI needs `blFeeDue` / `lenderFeeDue` equivalents in los-core.

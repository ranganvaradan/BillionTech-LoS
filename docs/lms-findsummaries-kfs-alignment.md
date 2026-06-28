# findSummaries, schedules, and KFS alignment (Phase 5)

## findSummaries → `lms_account_summary`

**bl-core:** `EncoreServiceFacadeImpl.toFindSummariesDto` maps vendor JSON into [`EncoreFindSummariesDto`](D:\Workspace\bl-core\code\src\main\java\com\sensei\micrograam\dto\EncoreFindSummariesDto.java) including `accountBalance` → `principalDue`, interest splits, `daysPastDue`, `totalDemandDue`, and nested fees.

**los-core (after this migration):**

- On-demand: [`LmsService.getAccountSummary`](D:\LOS\los-app\los-app\services\los-core-service\src\main\java\com\los\lms\service\LmsService.java) calls `encoreLmsApi.findSummaries` and applies [`EncoreSummaryNormalizer`](D:\LOS\los-app\los-app\services\los-core-service\src\main\java\com\los\lms\support\EncoreSummaryNormalizer.java).
- Scheduled: [`EncoreSummarySyncJob`](D:\LOS\los-app\los-app\services\los-core-service\src\main\java\com\los\lms\job\EncoreSummarySyncJob.java) uses the same normalizer for each active handover with `encore_account_id`.

**Field aliases supported (non-exhaustive):** `accountBalance`, `principalDue`, `payOffPrincipalDue`, `loanAmount` for outstanding principal; `operationalStatus`, `loanStatus`; `daysPastDue`, `dpd`; `overdueAmount`, `totalDemandDue`; `nextRepaymentDate`, `nextEmiDate`; `installmentAmount`, `nextInstallmentAmount` for next EMI amount.

**Remaining gap:** fee buckets and `accountStatementEntries` from findSummaries are **not** mirrored into `lms_account_summary` columns; extend the entity and normalizer if the collections or investor UI needs them.

## Repayment schedule

[`LmsService.getRepaymentSchedule`](D:\LOS\los-app\los-app\services\los-core-service\src\main\java\com\los\lms\service\LmsService.java) prefers Encore `findRepaymentSchedule` when active, mapping `sequenceNum`, `valueDateStr`, `installmentAmount`, `principalAmount`, `interestAmount`, `balance` into [`RepaymentScheduleEntry`](D:\LOS\los-app\los-app\services\los-core-service\src\main\java\com\los\lms\dto\RepaymentScheduleEntry.java). If Encore returns an empty list, falls back to local EMI math.

**Parity action:** When bl-core exposes different JSON keys for schedule lines, add those keys to the mapping lambda in `getRepaymentSchedule` (single place).

## KFS

los-core KFS pipeline: [`KfsService`](D:\LOS\los-app\los-app\services\los-core-service\src\main\java\com\los\core\service\kfs\KfsService.java), [`KfsPdfGenerationService`](D:\LOS\los-app\los-app\services\los-core-service\src\main\java\com\los\core\service\kfs\KfsPdfGenerationService.java), linked from [`LoanApplicationFlowService`](D:\LOS\los-app\los-app\services\los-core-service\src\main\java\com\los\core\service\loan\LoanApplicationFlowService.java) and eSign ([`EmsignerESignProvider`](D:\LOS\los-app\los-app\services\los-core-service\src\main\java\com\los\core\service\integration\providers\impl\EmsignerESignProvider.java)).

**Recommendation:** Before changing KFS templates, produce a **field matrix** (spreadsheet) listing each KFS numeric field, its source in los-core today, and the bl-core source (`EncoreFindSummariesDto`, local amortization, or sanction). Pull live Encore schedule into KFS only if regulatory wording requires **post-disbursement** vendor numbers.

## Job tuning

- `los.lms.encore.sync.cron` — defaults to nightly; increase frequency only if Encore rate limits allow.
- Consider passing `ignoreTransactions` policy in line with bl-core when investigating performance (los-core client already sends `false` to match legacy controller behaviour).

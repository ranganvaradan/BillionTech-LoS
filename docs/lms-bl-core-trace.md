# bl-core → los-core LMS trace (Phase 2)

Workspace: `D:\Workspace\bl-core` (verify this checkout matches production lineage).

## Primary façade

| Concern | bl-core | Notes |
|--------|---------|--------|
| Encore façade API | [`EncoreServiceFacade`](D:\Workspace\bl-core\code\src\main\java\com\sensei\micrograam\facade\EncoreServiceFacade.java) | Declares `openLoanAccount`, `disburse`, `repay`, `findSummaries`, `findSummary`, `findRepaymentSchedules`, `createProduct`, CASA helpers, reversals. |
| Implementation | [`EncoreServiceFacadeImpl`](D:\Workspace\bl-core\code\src\main\java\com\sensei\micrograam\facade\impl\EncoreServiceFacadeImpl.java) | Uses `EncoreHTTPClientServiceFacade` for HTTP GET/POST to Encore property keys (`encore.api.*`). |
| HTTP transport | [`EncoreHTTPClientServiceFacade`](D:\Workspace\bl-core\code\src\main\java\com\sensei\micrograam\facade\EncoreHTTPClientServiceFacade.java) / `EncoreHTTPClientServiceFacadeImpl` | Same physical endpoints as `los.lms.encore.api.*` in los-core. |

### `openLoanAccount` (loan create)

- **bl-core:** `EncoreServiceFacadeImpl.openLoanAccount` builds `LoanOdAccountWSDto`, sets `productCode` from `loan.getProduct().getProductCode()` (line ~412), posts `loanOdAccount` JSON via `encore.api.createloanaccount` (~452).
- **los-core:** [`DefaultEncoreLmsApi.openLoanAccount`](D:\LOS\los-app\los-app\services\los-core-service\src\main\java\com\los\encore\client\api\DefaultEncoreLmsApi.java) builds a JSON object with `productCode` from [`EncoreOpenLoanParams`](D:\LOS\los-app\los-app\services\los-core-service\src\main\java\com\los\encore\client\api\EncoreOpenLoanParams.java) (populated from `LoanHandoverRequest.productCode` via [`LmsService.toEncoreParams`](D:\LOS\los-app\los-app\services\los-core-service\src\main\java\com\los\lms\service\LmsService.java)).

### `disburse`

- **bl-core:** `EncoreServiceFacadeImpl.disburse` (~541) posts `Disbursement` transaction array via shared `postTransactions`.
- **los-core:** `DefaultEncoreLmsApi.disburse` — same pattern (`postTransactions` endpoint).

### `findSummaries`

- **bl-core:** `EncoreServiceFacadeImpl.findSummaries` (~566) GET with `accountId` JSON + `ignoreTransactions=false`, then `toFindSummariesDto` (~577) maps vendor JSON into [`EncoreFindSummariesDto`](D:\Workspace\bl-core\code\src\main\java\com\sensei\micrograam\dto\EncoreFindSummariesDto.java) (e.g. `accountBalance` → `principalDue`, `daysPastDue`, `totalDemandDue`, fees arrays).
- **REST usage:** [`RetailController`](D:\Workspace\bl-core\code\src\main\java\com\sensei\micrograam\web\rest\RetailController.java) calls `encoreService.findSummaries` and passes DTOs to `borrowerRepaymentAssembler.fromEncoreFindSummariesDto` (multiple locations, e.g. ~1373).
- **los-core:** [`EncoreLmsApi.findSummaries`](D:\LOS\los-app\los-app\services\los-core-service\src\main\java\com\los\encore\client\api\EncoreLmsApi.java) returns `List<Map<String,Object>>`; [`LmsService.getAccountSummary`](D:\LOS\los-app\los-app\services\los-core-service\src\main\java\com\los\lms\service\LmsService.java) and [`EncoreSummarySyncJob`](D:\LOS\los-app\los-app\services\los-core-service\src\main\java\com\los\lms\job\EncoreSummarySyncJob.java) persist into `lms_account_summary` using [`EncoreSummaryNormalizer`](D:\LOS\los-app\los-app\services\los-core-service\src\main\java\com\los\lms\support\EncoreSummaryNormalizer.java) to accept both raw JSON keys and DTO-oriented field names.

### `findSummary`

- **bl-core:** `EncoreServiceFacade.findSummary` used from `RetailController` (~3741, ~4844) for JSON snapshot per account.
- **los-core:** Endpoint configured on `EncoreClientProperties` (`find-summary`); not yet wrapped in `LmsService` for parity — tracked in Phase 3 gap list if needed.

### `findRepaymentSchedules`

- **bl-core:** `EncoreServiceFacade.findRepaymentSchedules`.
- **los-core:** `EncoreLmsApi.findRepaymentSchedule` consumed in [`LmsService.getRepaymentSchedule`](D:\LOS\los-app\los-app\services\los-core-service\src\main\java\com\los\lms\service\LmsService.java) with field mapping (`sequenceNum`, `valueDateStr`, `installmentAmount`, …).

### `createProduct`

- **bl-core:** `EncoreServiceFacadeImpl.createProduct` (~246–355) builds `LoanOdProductWSDto`, POST `encore.api.createProduct`, returns `productCode`.
- **los-core:** Product creation is **out of scope** for runtime LOS handover; products are provisioned in Encore or via ops. Mapping table supplies `productCode` per workflow segment.

## API dependency matrix (legacy → los-core)

| Legacy façade method | Encore-style operation | los-core |
|---------------------|-------------------------|----------|
| `createProduct` | POST create loan product | Config endpoint only; no Java wrapper required for disburse path |
| `openLoanAccount` | POST openAccount + `loanOdAccount` | `DefaultEncoreLmsApi.openLoanAccount` |
| `disburse` | POST postTransactions | `DefaultEncoreLmsApi.disburse` |
| `repay` | POST postTransactions | `DefaultEncoreLmsApi.repay` |
| `findSummaries` | GET findSummaries | `DefaultEncoreLmsApi.findSummaries` → `LmsService` / `EncoreSummarySyncJob` |
| `findSummary` | GET findSummary | Property present; optional future `LmsService` method |
| `findRepaymentSchedules` | GET/POST (as configured) | `DefaultEncoreLmsApi.findRepaymentSchedule` → `LmsService.getRepaymentSchedule` |
| `findAccountStatement` | GET statements | `DefaultEncoreLmsApi.getAccountStatement` → `LmsService.getEncoreAccountStatement` |

## KFS touchpoints

- **bl-core:** KFS and repayment UI paths consume `EncoreFindSummariesDto` and schedule DTOs after Encore calls (e.g. `RetailController` + repayment assembler). Exact KFS PDF field sources live in bl-core KFS/assembler modules (search `KFS`, `KeyFact`, `Sanction` in that repo for a full inventory).
- **los-core:** KFS generation is under `com.los.core.service.kfs` ([`KfsPdfGenerationService`](D:\LOS\los-app\los-app\services\los-core-service\src\main\java\com\los\core\service\kfs\KfsPdfGenerationService.java), [`KfsService`](D:\LOS\los-app\los-app\services\los-core-service\src\main\java\com\los\core\service\kfs\KfsService.java)), document entity [`KfsDocument`](D:\LOS\los-app\los-app\services\los-core-service\src\main\java\com\los\core\model\entity\KfsDocument.java), eSign flow referencing `KFS_AGREEMENT`. **Parity gap:** los-core KFS should eventually consume the same **schedule shape** and **outstanding principal** semantics as bl-core after disburse; today much of KFS is driven from loan application / sanction data, not live Encore schedule rows. Align when product requires field-level match (see `lms-migration-strategy.md`).

## Risks

- **DTO vs Map:** bl-core normalizes Encore JSON into rich DTOs; los-core historically read only `accountBalance` and `operationalStatus`. `EncoreSummaryNormalizer` reduces drift but does not yet parse nested `fees` arrays like bl-core `toFindSummariesDto`.
- **Customer / party:** bl-core `openLoanAccount` binds `customerId1` to party id and address fields from `PartyDto`. los-core [`DefaultEncoreLmsApi`](D:\LOS\los-app\los-app\services\los-core-service\src\main\java\com\los\encore\client\api\DefaultEncoreLmsApi.java) uses `applicationNumber` as `customerId1` — acceptable for pilot but not full micrograam parity.

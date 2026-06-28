# Status Matrix

## Important
This is a working blueprint and must be aligned with actual enums and transitions in the codebase.

## 1. Application lifecycle status
Represents business process position.

| Status | Meaning | Owner module |
|---|---|---|
| DRAFT | Application started but not submitted | Application Lifecycle |
| SUBMITTED | Submitted for processing | Application Lifecycle |
| KYC_IN_PROGRESS | KYC orchestration underway | Application Lifecycle + KYC |
| KYC_FAILED | KYC blocked or failed | Application Lifecycle + KYC |
| BUREAU_PENDING | Awaiting bureau pull or review | Underwriting |
| UNDERWRITING | Underwriting evaluation active | Underwriting |
| APPROVED | Credit decision approved | Underwriting |
| REJECTED | Credit decision rejected | Underwriting |
| SANCTION_ISSUED | Sanction prepared/issued | Fulfillment |
| ESIGN_PENDING | Awaiting signature completion | Fulfillment |
| DISBURSEMENT_PENDING | Ready for disbursement checks | Fulfillment |
| DISBURSED | Money disbursed / finalized | Fulfillment |

## 2. KYC decision state
Represents derived KYC result.

| State | Meaning |
|---|---|
| PASS | Required KYC conditions satisfied |
| FAIL | Required KYC conditions failed |
| INCOMPLETE | Not enough evidence or pending review |

## 3. KYC step state
Represents operational result per step.

| State | Meaning |
|---|---|
| PENDING | Not yet executed |
| SUCCESS | Step passed |
| FAILURE | Step failed |
| MANUAL_REVIEW | Needs human decision |
| ERROR | Technical or provider failure |
| OVERRIDDEN | Human override applied |

## Design rule
UI and APIs should expose these concepts separately where confusion is likely.

# Module: Application Lifecycle

## Scope
This module owns the lifecycle of a loan application as a business case.

## Responsibilities
- Create, update, and retrieve loan applications
- Control lifecycle state transitions
- Handle submission and resubmission
- Maintain notes, metadata, and timeline markers
- Enforce which downstream stages can start

## Must not own
- Raw KYC step execution logic
- Provider-specific bureau integrations
- Direct disbursement execution

## Canonical status examples
These should be aligned with actual enums before final adoption:
- DRAFT
- CONSENT_PENDING
- SUBMITTED
- KYC_IN_PROGRESS
- KYC_FAILED
- BUREAU_PENDING
- UNDERWRITING
- APPROVED
- REJECTED
- SANCTION_ISSUED
- ESIGN_PENDING
- DISBURSEMENT_PENDING
- DISBURSED
- CANCELLED

## Rules
- Application lifecycle status represents where the case sits in the business process.
- It is not the same as KYC outcome.
- It is not the same as underwriting decision explanation.
- Direct status edits should be avoided unless explicitly allowed for admin or repair use cases.

## Typical AI tasks in this module
- Add a new application field end-to-end
- Fix submission or withdrawal rules
- Tighten allowed transitions
- Add timeline or audit markers

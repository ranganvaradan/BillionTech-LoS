# LMS customer / party onboarding (deferred)

## Phase 4 optional item: idempotent Encore customer create

**Status:** Not implemented in application code. Schema support only: `loan_applications.lms_encore_customer_id` (Flyway `V45__workflow_lms_product_mapping.sql`) is nullable and unused by services.

## bl-core behaviour (reference)

`EncoreServiceFacadeImpl.openLoanAccount` sets `customerId1` from `PartyDto.getId()` and populates address fields from `CityDto` / partner branch. That implies a **stable party identifier** in the legacy world before loan account creation.

## los-core today

[`DefaultEncoreLmsApi.openLoanAccount`](D:\LOS\los-app\los-app\services\los-core-service\src\main\java\com\los\encore\client\api\DefaultEncoreLmsApi.java) sets `customerId1` to the **application number** string. This is sufficient when Encore does not enforce pre-existing customer records.

## When to implement

- Encore environment **rejects** `openLoanAccount` without a registered customer, or
- Compliance mandates **party-level** identity in Encore matching KYC golden source.

## Suggested implementation (future PR)

1. Add `LmsCustomerOnboardingService` under `com.los.lms` calling a documented Encore “create customer” or reuse `findAccountsForCustomer` + create if empty (confirm vendor API).
2. Wrap with idempotency: read `lms_encore_customer_id` on `LoanApplication`; if set, skip create; else POST once and persist id in the same transaction as handover (or before `openLoanAccount`).
3. Wire from `LmsService.handoverLoan` **before** `openLoanAccount` only when a property such as `los.lms.customer-onboarding.enabled` is true.

## Risk

Duplicate customer rows in Encore if retries race — mitigate with unique business key (PAN hash / application id) and Encore-side deduplication policy.

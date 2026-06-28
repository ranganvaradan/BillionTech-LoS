# LMS integration PR checklist (Phase 6)

Use this checklist for each vertical slice (mapping, handover, sync, KFS, customer onboarding).

## Every PR

- **WHY:** One sentence on business or technical motivation.
- **IMPACT:** Who is affected (borrowers, ops, integrations); default-config behaviour vs opt-in.
- **RISK:** What could go wrong; how to detect it in logs or DB (`lms_loan_handover.handover_status`, Encore HTTP errors).

## Compatibility

- [ ] Default configuration preserves previous behaviour (`los.lms.workflow-mapping.enabled=false` unless explicitly changed).
- [ ] No breaking change to public REST paths without version bump.
- [ ] Flyway migration is additive or clearly documented if destructive.
- [ ] `LoanHandoverRequest` JSON consumers still receive `loanProduct`; only `productCode` semantics change when mapping is on.

## Code constraints (from program plan)

- [ ] **Avoid** algorithm changes inside [`WorkflowEngineServiceImpl`](D:\LOS\los-app\los-app\services\los-core-service\src\main\java\com\los\core\service\workflow\WorkflowEngineServiceImpl.java) except injecting resolved values into existing step execution paths where already supported.
- [ ] Prefer new types under `com.los.lms` (resolver, support, config) over scattering Encore logic in unrelated packages.

## Testing

- [ ] `mvn -pl services/los-core-service test` (or full reactor) passes.
- [ ] Manual: handover with Encore stub/real server confirms `productCode` in Encore request matches mapping row when flag is true.

## Rollback

- [ ] Feature can be turned off via configuration without redeploy (mapping flag).
- [ ] DB rollback documented if migration must be reversed (down migration or corrective `V46` script per team policy).

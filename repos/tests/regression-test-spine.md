# Regression Test Spine

These are the first automated tests recommended for `services/los-core-service`.

## Lifecycle
1. Create application defaults to expected initial state.
2. Submit application transitions from DRAFT to the next allowed processing state.

## KYC
3. Mandatory KYC failure sets the expected blocking lifecycle state.
4. Manual KYC override changes effective KYC decision state as designed.
5. KYC summary endpoint returns lifecycle status, KYC decision state, and step detail separately.

## Underwriting
6. Underwriting cannot start if prerequisite KYC condition is not satisfied.
7. Underwriting APPROVED path moves to the correct next lifecycle state.
8. Underwriting REJECTED path moves to the correct terminal or blocking state.

## Fulfillment
9. Sanction issuance allowed only from approved state.
10. eSign/disbursement readiness path enforces required prerequisites.

## Test style recommendation
- Prefer service-level tests for business transitions.
- Add controller-level contract tests for critical endpoints.
- Use representative fixtures, not huge random datasets.

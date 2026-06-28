# Module: Credit and Underwriting

## Scope
This module owns credit evaluation, policy checks, score usage, underwriting review, and decision support.

## Responsibilities
- Accept bureau and related risk inputs
- Run policy and eligibility checks
- Produce decision recommendations
- Support manual underwriting review where needed
- Record underwriting rationale and audit trail

## Inputs
- Application data
- KYC pass or acceptable readiness state
- Bureau and score data
- Policy configuration
- Product constraints

## Outputs
- APPROVED recommendation
- REJECT recommendation
- REFER or MANUAL_REVIEW recommendation where supported

## Rules
- Underwriting should not start unless prerequisite KYC requirements are satisfied.
- Decision states should be explicit and auditable.
- Approval should not automatically imply sanction issuance unless business rules say so.

## Typical AI tasks in this module
- Add a rule to policy evaluation
- Fix score threshold logic
- Add underwriting notes or reason capture
- Tighten readiness checks before underwriting

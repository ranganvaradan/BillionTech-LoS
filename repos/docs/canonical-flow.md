# Canonical Flow

## Objective
Reduce ambiguity by defining one business flow path for normal operations.

## Normal path
1. Create application
2. Submit application
3. Run KYC orchestration
4. Run bureau pull and readiness checks
5. Run underwriting
6. Approve or reject
7. Issue sanction
8. Complete KFS / eSign / mandate steps as required
9. Mark disbursement pending
10. Disburse

## Rule
Normal lifecycle progression should happen through orchestration services and flow-aware business methods.

## Admin/repair path
Direct transition endpoints, if retained, should be documented as admin-only or repair-only.

## Why this matters
Without a canonical flow, AI and developers will keep changing scattered transition points, creating inconsistent state behavior and expensive debugging loops.

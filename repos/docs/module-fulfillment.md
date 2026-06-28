# Module: Post-Approval Fulfillment

## Scope
This module owns everything after approval and before/through disbursement.

## Responsibilities
- KFS generation and acceptance tracking
- eSign orchestration
- NACH readiness or mandate steps
- Disbursement readiness checks
- Handover to LMS adapter where required

## Rules
- Only approved applications may move to sanction issuance.
- Only sanctioned applications may move to eSign.
- Only applications satisfying fulfillment checks may move to disbursement pending or disbursed.
- Integration status and business status must be separated where possible.

## Typical AI tasks in this module
- Add readiness check before disbursement
- Fix eSign callback mapping
- Add KFS acceptance validation

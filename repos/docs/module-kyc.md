# Module: KYC Orchestration

## Scope
This module owns KYC workflow orchestration and the effective KYC decision state for an application.

## Core concepts

### KYC step state
Operational result of each step, such as:
- PENDING
- SUCCESS
- FAILURE
- MANUAL_REVIEW
- ERROR
- OVERRIDDEN

### KYC decision state
Derived overall KYC result, such as:
- PASS
- FAIL
- INCOMPLETE

### Application lifecycle status
Business process position, such as:
- KYC_IN_PROGRESS
- KYC_FAILED
- UNDERWRITING

These concepts must not be treated as interchangeable.

## Responsibilities
- Execute KYC steps in sequence or according to workflow
- Persist step results
- Support manual review and overrides
- Compute effective KYC decision state
- Expose KYC summary to UI and downstream processes

## Rules
- KYC outcome must be derived from step results and manual review decisions.
- Application status may move due to KYC, but application status is still a workflow state, not a direct substitute for KYC outcome.
- Provider-specific behavior belongs in the integrations layer, not the orchestration layer.

## Known risk area
A case may show a KYC-related application status that appears inconsistent with computed KYC outcome if the distinction between workflow state and derived KYC decision is not documented clearly in code and UI.

## Typical AI tasks in this module
- Diagnose mismatch between KYC outcome and application status
- Add manual override behavior
- Improve KYC summary APIs
- Add retry or fallback logic for specific KYC steps

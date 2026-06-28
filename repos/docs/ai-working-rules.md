# AI Working Rules

## Purpose
This file is the mandatory instruction set for any AI assistant working on this repository.

## Mandatory behavior
- Work only within the requested module unless the task explicitly requires cross-module changes.
- Read the relevant `docs/module-*.md` file before proposing code changes.
- Propose a plan before editing code.
- List impacted files before making changes.
- Prefer minimal, targeted edits over broad refactors.
- Do not introduce new dependencies unless explicitly approved.
- Do not introduce new status values unless explicitly approved.
- Do not change API contracts silently.
- Do not edit stable shell services unless the task explicitly targets them.
- Do not use or extend mock fallback behavior in production code paths.

## Status and decision rules
- Application lifecycle status is not the same as KYC outcome.
- KYC outcome is not the same as KYC step result.
- Underwriting decision state is not the same as application lifecycle status.
- When diagnosing state mismatches, enumerate all code paths that can write each value.

## Testing rules
- Every bug fix must include or update a regression test where feasible.
- Every lifecycle change must include transition validation.
- Every API contract change must include at least one contract-level verification.

## Output format for AI tasks
The AI should return:
1. Plan
2. Impacted files
3. Proposed changes
4. Risks
5. Validation steps

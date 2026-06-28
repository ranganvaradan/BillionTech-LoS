# API Contracts Working Rules

## Purpose
This document is not a full API catalog. It defines contract rules for API changes.

## Contract principles
- Keep response envelopes consistent.
- Avoid changing field names in existing UI contracts without versioning or coordinated frontend changes.
- Preserve identifiers used by existing pages.
- Avoid returning provider-specific raw structures directly to the frontend.
- For stage summary endpoints, separate workflow status, decision state, and step-level detail.

## Change protocol
Before changing an API:
1. List impacted frontend pages.
2. List impacted backend services.
3. Confirm whether the change is additive, breaking, or behavioral.
4. Update docs and tests.

## Specific recommendation for this repo
For application, workflow, and KYC APIs, document separately:
- lifecycle status
- KYC decision state
- KYC step results
- underwriting recommendation state

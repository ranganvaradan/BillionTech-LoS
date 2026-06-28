# LOS Overview

## Purpose
This repository implements a Loan Origination System (LOS) with a Next.js frontend and Spring Boot microservices backend. The primary business complexity sits inside `services/los-core-service`, while the surrounding services provide gateway, discovery, identity, notification, enrollment, and LMS integration support.

## Current service landscape

### Frontend
- `frontend/` — Next.js + TypeScript + Tailwind application

### Platform services
- `services/api-gateway/`
- `services/discovery-service/`
- `services/iam-service/`
- `services/notification-service/`
- `services/enrollment-service/`
- Encore LMS: remote HTTP via `ENCORE_BASE_URL` (see `docs/encore-lms-integration.md`)
- `services/los-core-service/` (includes LMS → vendor Encore HTTP client)

### Core business service
- `services/los-core-service/` — main business engine for application lifecycle, KYC, underwriting, documents, audit, workflow orchestration, reporting, collateral, co-lending, and disbursement readiness.

## Development model
For day-to-day work, do not treat all services as equally active.

### Stable shell
Normally avoid touching these unless the task explicitly requires it:
- API gateway
- Discovery service
- Notification service
- IAM service

### Edge service
Touch only when onboarding or intake changes are required:
- Enrollment service

### Core business service
Default backend work should happen here:
- LOS core service

### Frontend
Treat as a separate workstream unless there is an API contract change.

## Canonical engineering principle
The system must have one clearly documented, canonical business flow. AI and developers should not infer flow rules from scattered endpoints or UI pages.

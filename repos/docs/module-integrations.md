# Module: Integrations

## Scope
This module owns external provider interactions and adapter boundaries.

## Responsibilities
- KYC provider calls
- Bureau calls
- LMS adapter communication
- Callback handling and payload mapping
- Partner-specific routing and configuration

## Rules
- Provider-specific logic should be isolated from business orchestration.
- Do not leak provider response shapes into UI contracts.
- Do not implement business status transitions directly in provider classes unless the design explicitly requires it.
- Favor mapping provider responses into internal normalized objects.

## Typical AI tasks in this module
- Add or update provider mapping
- Handle callback retries
- Normalize error handling
- Add audit payload capture

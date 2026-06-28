# LOS Repo-Ready Starter Pack

This pack is designed for the current LOS codebase structure:

- `frontend/`
- `services/api-gateway/`
- `services/discovery-service/`
- `services/enrollment-service/`
- `services/iam-service/`
- Encore LMS: configure `ENCORE_BASE_URL`, `ENCORE_API_USERNAME`, `ENCORE_API_PASSWORD` (remote API only)
- `services/los-core-service/` (LMS integration lives here)
- `services/los-core-service/`
- `services/notification-service/`

## Recommended placement

- Copy `docs/*` into the repo root under `docs/`
- Copy `prompts/*` into the repo root under `prompts/`
- Copy `checklists/*` into the repo root under `checklists/`
- Copy `tests/*` into the repo root under `tests/` or convert into your preferred format

## Immediate next steps

1. Add `docs/ai-working-rules.md` to the repo first.
2. Add `docs/status-matrix.md` and align names with actual enums in `los-core-service`.
3. Make orchestration flow canonical and mark direct transition endpoints as admin-only where appropriate.
4. Convert `tests/regression-test-spine.md` into automated tests in `services/los-core-service`.
5. Use `prompts/module-task-template.md` for all AI-assisted changes.

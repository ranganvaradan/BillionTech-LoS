# BillionTech LoS

Validated Loan Origination System handoff branch.

This repository layout matches the original laptop / service structure:

```
BillionTech-LoS/
├── los-core-service/      # authoritative backend
├── ui-service/            # authoritative frontend (Vite/React)
├── discovery-service/
├── notification-service/
├── docs/
└── infra/                 # safe examples only (no real .env)
```

## Source checkpoints imported

| Service | Checkpoint |
|---------|------------|
| los-core-service | `7637e26c771d178174239b995cfa76e8973438b9` |
| ui-service | `091e1642aa0df0af59647c36edf2840627547569` |
| discovery-service | `b8f60c7` |
| notification-service | `db04b7b` |

See `LOS_LOCAL_CHECKPOINT_MANIFEST.md` for sync date, previous handoff HEAD, Flyway tip (V118), and gate coverage.

## Build (same as laptop)

```bash
# Backend
cd los-core-service
mvn -DskipTests compile
mvn test

# Frontend
cd ui-service
npm ci
npm test
npx tsc -b
npm run build

# Supporting services
cd discovery-service && mvn -DskipTests compile
cd notification-service && mvn -DskipTests compile
```

## Notes

- Flyway migrations live under `los-core-service/src/main/resources/db/migration` (V1–V111 in this checkpoint).
- Credit Intelligence / Policy Studio remains non-authoritative (`allow-canonical-authority: false`).
- Do not commit `data/documents`, `.env`, or borrower uploads.
- Reference branch with monorepo-oriented layout (kept for history): `reconcile/current-los-2026-08`.

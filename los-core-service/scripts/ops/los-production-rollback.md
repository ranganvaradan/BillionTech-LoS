# LOS production/staging rollback plan

## Pre-deploy

1. Full DB backup (see `los-db-backup-restore.md`) — size &gt; 0 + sha256 recorded.
2. Record previous image tags:
   - `docker inspect billiontechlos-core --format '{{.Image}}'`
   - UI image/tag similarly
3. Record Flyway tip: `SELECT version, description FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;`
4. Record git HEADs for `los-core-service` and `ui-service`.

## Application image rollback

```bash
cd /opt/billiontech/apps/billiontechlos
# restore previous compose/image tag, then:
docker compose pull core   # or load previous tag
docker compose up -d core
curl -sf http://127.0.0.1:<port>/actuator/health
```

## UI rollback

Redeploy previous UI build/tag; smoke login + intake page load.

## DB migration rollback

- Prefer **forward-fix** migrations.
- If a new migration is unsafe: restore disposable verification first; for emergency only restore from pre-deploy dump into a new DB and cut traffic after validation.
- **Never** edit applied Flyway scripts; never restore over active staging/prod without explicit change window.

## Config rollback

Revert env vars / compose env file to previous values (`LOS_JWT_HMAC_SECRET`, `CREDIT_INTELLIGENCE_INTERNAL_TOKEN`, provider URLs). Restart core. Confirm actuator `productionHardening` details show expected flags.

## Health verification

- `/actuator/health` UP
- `productionHardening` component: demo/gap/canonicalAuthority safe
- Login issues JWT (prod)
- Golden smoke: compose COMPANY/TERM_LOAN readiness; no anonymous document download

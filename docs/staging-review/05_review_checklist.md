# 05 — Review checklist

## Before the CEO session

- [ ] `los_core_staging` database created (not production)
- [ ] Backend running with `--spring.profiles.active=staging`
- [ ] `GET .../staging-demo/health` returns `allowCanonicalAuthority: false`
- [ ] UI `npm run dev` and Credit Intelligence nav visible
- [ ] Fixture banner visible on Applications page

## Product proof points

- [ ] CASE_A workspace builds evidence + shadow decision + stub AI
- [ ] Replay button reports matching hashes
- [ ] CASE_B dual-run shows legacy-default difference classes
- [ ] Policy Studio Banking / Bureau shows three columns
- [ ] No page claims production authority / real borrower data

## Hard no-gos

- [ ] Do **not** set `allow-canonical-authority: true`
- [ ] Do **not** wire production AI-LOS / CAM / sanction from this review
- [ ] Do **not** use production Postgres credentials

## Commands (quick)

```bash
# create DB
createdb los_core_staging  # or docker

# start
cd los-core-service
mvn -DskipTests spring-boot:run -Dspring-boot.run.profiles=staging

# UI
cd ui-service
npm run dev
```

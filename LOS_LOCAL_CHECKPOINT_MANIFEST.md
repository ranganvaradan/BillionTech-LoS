# LOS Handoff Checkpoint Manifest

**Updated:** 2026-08-11 (LOS-GITHUB-HANDOFF-FINAL-SYNC)  
**Branch:** `reconcile/laptop-layout-2026-08`  
**Handoff previous HEAD:** `fc1287f61e9f7c49b18433d049709870be947e1a`  
**Sync mechanism:** controlled file-tree import from service-repo committed HEADs (`git archive`), not cherry-pick / not shared history.

## Authoritative source HEADs (this snapshot)

| Service | Origin | Source HEAD |
|---------|--------|-------------|
| los-core-service | `https://repo.billionloans.com/LOS/los-core-service.git` | `7637e26c771d178174239b995cfa76e8973438b9` |
| ui-service | `https://repo.billionloans.com/LOS/ui-service.git` | `091e1642aa0df0af59647c36edf2840627547569` |
| discovery-service | (unchanged from prior handoff) | `b8f60c7910bd44355fcb5bfc8f739a07f0c32cec` |
| notification-service | (unchanged from prior handoff) | `db04b7ba6e6d3e59f1b86d6e0c4d5e341af828ce` |

## Safety / policy

- `allowCanonicalAuthority=false` / `allow-canonical-authority: false`
- Flyway tip in this snapshot: **V118** (`V118__decision_configuration_snapshot.sql`)
- Borrower `data/documents/**` **excluded** from handoff commit (PII)
- Generated tarballs / `target/` / `node_modules/` / `dist/` **not** imported
- Uncommitted one-off staging scripts from dirty laptop trees **not** imported

## Gates represented (post prior handoff SCORECARD-GOVERNANCE tip)

- GACAT persistence / catalogue
- Scorecard safety / convergence / governance
- Product Config / Routing convergence
- Policy Data Resolution UX
- LMS Product Mapping P0
- LOS Golden E2E support scripts
- Production Hardening (JWT/RBAC, snapshots, go-live validator)
- Production P0 Closure (single-tenant fail-closed, reproducibility)
- Customer Cutover support (Day-1 compose/readiness, cutover record/checklist ops docs)

## Ops docs included (from committed CORE HEAD only)

- `los-core-service/scripts/ops/LOS-CUSTOMER-CUTOVER-1-RECORD.md`
- `los-core-service/scripts/ops/los-production-rollback.md`
- `los-core-service/scripts/ops/provider-readiness-*.md`
- `los-core-service/scripts/ops/rbac-permission-matrix.md`
- `los-core-service/scripts/ops/customer-go-live-config-template.md`
- `los-core-service/scripts/ops/prod-profile-dry-run.md`
- `los-core-service/scripts/ops/los-db-backup-restore.md`

Uncommitted laptop-only P0-FINAL verification scripts were **excluded**.

# LOS full DB backup / restore (staging)

Use the `los_app` role (or the role that owns `los_core_staging`) via the Postgres container.
Do **not** use a wrong role that yields 0-byte dumps.

## Backup (staging host)

```bash
TS=$(date -u +%Y%m%dT%H%M%SZ)
OUT=/opt/billiontech/backups/los_core_staging_${TS}.dump
mkdir -p /opt/billiontech/backups
docker exec billiontech-postgres pg_dump -U los_app -d los_core_staging -Fc -f /tmp/los_dump.dump
docker cp billiontech-postgres:/tmp/los_dump.dump "$OUT"
BYTES=$(wc -c < "$OUT")
SHA=$(sha256sum "$OUT" | awk '{print $1}')
echo "BYTES=$BYTES"
echo "SHA256=$SHA"
test "$BYTES" -gt 0
```

## Restore into disposable DB (do not overwrite active staging)

```bash
DUMP=/opt/billiontech/backups/los_core_staging_<TS>.dump
docker exec -i billiontech-postgres psql -U postgres -c "DROP DATABASE IF EXISTS los_core_restore_test;"
docker exec -i billiontech-postgres psql -U postgres -c "CREATE DATABASE los_core_restore_test OWNER los_app;"
docker cp "$DUMP" billiontech-postgres:/tmp/los_restore.dump
docker exec billiontech-postgres pg_restore -U los_app -d los_core_restore_test --clean --if-exists /tmp/los_restore.dump
docker exec billiontech-postgres psql -U los_app -d los_core_restore_test -c \
  "SELECT 'loan_applications' t, count(*) c FROM loan_applications
   UNION ALL SELECT 'workflow_configs', count(*) FROM workflow_configs
   UNION ALL SELECT 'underwriting_evaluations', count(*) FROM underwriting_evaluations;"
```

## Pre-cutover baseline (LOS-PRODUCTION-HARDENING-1)

Recorded staging backup:

- Path: `/opt/billiontech/backups/los_core_staging_pre_los_production_hardening_1_20260811T142824Z.dump`
- BYTES=`775426`
- SHA256=`23e14945461772e7bd1cdb2d0cb4b8f05470975f5330f75d8ca8ed991518da09`

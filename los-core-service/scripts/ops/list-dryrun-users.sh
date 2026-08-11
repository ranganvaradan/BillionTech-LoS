#!/bin/bash
set -eu
docker exec billiontech-postgres psql -U los_app -d los_core_prod_dryrun -c \
  "SELECT email, role FROM users ORDER BY role, email LIMIT 40;"
docker exec billiontech-postgres psql -U los_app -d los_core_prod_dryrun -c \
  "SELECT id::text FROM loan_applications LIMIT 3;"

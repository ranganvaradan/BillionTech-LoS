#!/bin/bash
set -eu
docker exec billiontech-postgres psql -U los_app -d los_core_staging -c \
  "SELECT program_name, product_type, plp_program_id, plp_operational_status, encore_product_code FROM program_masters LIMIT 25;"
docker exec billiontech-postgres psql -U los_app -d los_core_staging -c \
  "SELECT primary_los_role, count(1) AS n FROM los_users GROUP BY 1 ORDER BY 1;"
docker exec billiontech-postgres psql -U los_app -d los_core_staging -c \
  "SELECT role_code, count(1) AS n FROM user_role_mappings GROUP BY 1 ORDER BY 1;"

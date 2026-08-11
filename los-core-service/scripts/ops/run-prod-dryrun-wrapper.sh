#!/bin/bash
set -eu
sed -i 's/\r$//' /tmp/prod-profile-jvm-dry-run.sh || true
export LOS_DB_USER=$(docker exec billiontechlos-core printenv POSTGRES_USER)
export LOS_DB_PASSWORD=$(docker exec billiontechlos-core printenv POSTGRES_PASSWORD)
export REDIS_HOST=$(docker exec billiontechlos-core printenv REDIS_HOST)
export REDIS_PORT=$(docker exec billiontechlos-core printenv REDIS_PORT)
export REDIS_PASSWORD=$(docker exec billiontechlos-core printenv REDIS_PASSWORD)
export RABBITMQ_HOST=$(docker exec billiontechlos-core printenv RABBITMQ_HOST)
export RABBITMQ_PORT=$(docker exec billiontechlos-core printenv RABBITMQ_PORT)
export RABBITMQ_USER=$(docker exec billiontechlos-core printenv RABBITMQ_USER)
export RABBITMQ_PASSWORD=$(docker exec billiontechlos-core printenv RABBITMQ_PASSWORD)
test -n "${LOS_DB_PASSWORD}"
test -n "${REDIS_HOST}"
bash /tmp/prod-profile-jvm-dry-run.sh

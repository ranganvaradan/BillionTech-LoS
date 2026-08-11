#!/usr/bin/env bash
set -euo pipefail
for i in $(seq 1 18); do
  st=$(docker inspect billiontechlos-core --format '{{.State.Health.Status}}' 2>/dev/null || echo missing)
  echo "health=$st"
  if [ "$st" = "healthy" ]; then break; fi
  sleep 5
done
tr -d '\r' < /tmp/lifecycle-golden-walk2.sh > /tmp/g2.sh
bash /tmp/g2.sh

# LOS deployment port mapping (production / shared EC2)

## Why UI is not on host port 80

On Credinnov EC2, **host nginx** owns port **80** (team LMS sandbox, reverse proxy). The LOS Docker UI must not bind `80:80` or it conflicts with nginx and blocks `los_ui` from starting.

**Default:** `los_ui` maps **host 8080 → container 80** via `LOS_UI_HOST_PORT` in compose.

## Port table (`docker-compose.prod.yml`)

| Service | Container | Host port | Notes |
|---------|-----------|-----------|--------|
| **ui-service** | `los_ui` | **8080** (configurable) | App path **`/los/`** — host nginx include → `127.0.0.1:8080/los/` (see `deploy/nginx/`) |
| discovery-service | `los_discovery` | 8761 | Ops / debugging only |
| notification-service | `los_notification` | 8084 | Internal or ops |
| redis | `los_redis` | 6379 | Do not expose on public SG if possible |
| rabbitmq | `los_rabbitmq` | 5672, 15672 | Do not expose publicly |
| los-core | `los_core` | *(none)* | 8083 internal; UI proxies `/api/` |
| postgres | `los_postgres` | *(none)* | Internal only |

**Removed:** vendor Encore 3307, 8090, 9080 — LMS is remote (`ENCORE_BASE_URL`).

## Configuration

In `.env.prod` (optional override):

```env
LOS_UI_HOST_PORT=8080
```

## Host nginx (team or sudo)

Use the additive snippet in `deploy/nginx/los-host-locations.conf` (`include snippets/los-host-locations.conf;` inside the existing `server` block). Do not modify `/credinnov-encore-server/` or other team locations.

## Troubleshooting `los_core` unhealthy

1. **Stop orphan vendor Encore containers** (if still running from an old compose):  
   `docker stop los_encore_client los_encore_server los_encore_mysql`
2. **Redis auth:** prod compose sets `SPRING_DATA_REDIS_PASSWORD=""` because `los_redis` has no password.
3. **Logs:** `docker logs los_core --tail 150` and  
   `docker exec los_core wget -qO- http://localhost:8083/actuator/health`

## Verify after deploy

```bash
docker compose -f docker-compose.prod.yml up -d --build
curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:8080/
docker ps --filter name=los_ui --format "{{.Ports}}"
```

## PLP (separate repo)

PLP uses **3100 / 3200 / 3300** (UIs) and **8180** (gateway) — no conflict with LOS 8080. See PLP `docker-compose.yml` + `docker-compose.ui.yml`.

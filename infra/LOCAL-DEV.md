# Local dev (mirror UAT layout, build from source)

Private registry `artifacts.billionloans.com` is for UAT/CI only. Locally, **build from** `D:\BT-LOS-WORKSPACE\PLP` and `LOS`.

Build paths: `infra/los` → `../../LOS/...` · `infra/plp/*` → `../../../PLP/...`

Host nginx snippets for combined LOS+PLP on one EC2: `infra/deploy/nginx/` (from GitHub `LOS_APP` monorepo).

HDFC advance payments local curl and regression checklist: `PLP/docs/HDFC_ADVANCE_PAYMENTS.md`.

## One-time

```powershell
docker network create los_nw
docker network create plp_nw
copy D:\BT-LOS-WORKSPACE\infra\los\.env.example D:\BT-LOS-WORKSPACE\infra\los\.env
copy D:\BT-LOS-WORKSPACE\infra\plp\ui-services\env-config.local.js D:\BT-LOS-WORKSPACE\infra\plp\ui-services\env-config.js
```

## Start (order matters)

### 1. PLP backends (~15–30 min first build)

```powershell
cd D:\BT-LOS-WORKSPACE\infra\plp\backend-services
docker compose -f docker-compose.yml -f docker-compose.local-build.yml -f docker-compose.local-env.yml up -d --build
```

### 2. PLP UIs

```powershell
cd D:\BT-LOS-WORKSPACE\infra\plp\ui-services
docker compose -f docker-compose.yml -f docker-compose.local-build.yml up -d --build
```

### 3. LOS stack

```powershell
cd D:\BT-LOS-WORKSPACE\infra\los
docker compose -f docker-compose.yml -f docker-compose.local-build.yml -f docker-compose.local-env.yml up -d --build
```

## URLs

| App | URL |
|-----|-----|
| PLP API | http://localhost:8180 |
| PLP platform | http://localhost:3100/plp/ |
| PLP anchor | http://localhost:3200/plp-anchor/ |
| PLP borrower | http://localhost:3300/plp-borrower/ |
| LOS | http://localhost:8080/los/ |

Login: `admin@credinnov.com` / `Bltest@123`

## Optional: registry login (UAT images)

If DevOps gives credentials:

```powershell
docker login artifacts.billionloans.com:5001
docker login artifacts.billionloans.com:5002
docker compose up -d   # without local-build overlays
```

## Rebuild one service after code change

```powershell
docker compose -f docker-compose.yml -f docker-compose.local-build.yml up -d --build lending-service
```

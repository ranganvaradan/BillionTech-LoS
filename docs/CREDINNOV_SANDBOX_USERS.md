# Credinnov sandbox — login users

**Branch:** `credinnov`  
**Password (all users):** `Bltest@123`  
**LOS URL:** `http://credinnov-sandbox.senseitech.com/los/`  
**PLP platform:** `http://credinnov-sandbox.senseitech.com/plp/`  
**PLP anchor:** `http://credinnov-sandbox.senseitech.com/plp-anchor/`  
**PLP borrower:** `http://credinnov-sandbox.senseitech.com/plp-borrower/`  
**Encore LMS UI:** `http://credinnov-sandbox.senseitech.com/encore-client/` (`admin` / `password1` — same as `.env.prod`)

Billionloans demo users are **deactivated** when migration `V57__seed_credinnov_los_auth_users.sql` (LOS) / `V3__seed_credinnov_users.sql` (PLP) runs.

## LOS (`los_users`)

| Display name | Email | Role |
|--------------|-------|------|
| Admin | admin@credinnov.com | Administrator |
| Credit Manager | creditmanager@credinnov.com | Credit Manager |
| Credit Officer | creditofficer@credinnov.com | Credit Officer |
| Credit Officer 2 | creditofficer2@credinnov.com | Credit Officer |
| Sales | sales@credinnov.com | Sales |
| Accounts | accounts@credinnov.com | Accounts |
| Borrower | borrower@credinnov.com | Borrower |

## PLP (`plp_iam.users`)

| Display name | Email | PLP role | Portal |
|--------------|-------|----------|--------|
| Admin | admin@credinnov.com | PLATFORM_ADMIN | `/plp/` |
| Credit Manager | creditmanager@credinnov.com | CREDIT_MANAGER | `/plp/` |
| Credit Officer | creditofficer@credinnov.com | CREDIT_ANALYST | `/plp/` |
| Credit Officer 2 | creditofficer2@credinnov.com | CREDIT_ANALYST | `/plp/` |
| Sales | sales@credinnov.com | COMPLIANCE_OFFICER | `/plp/` |
| Accounts | accounts@credinnov.com | ACCOUNTS_OFFICER | `/plp/` |
| Anchor Admin | anchor@credinnov.com | ANCHOR_ADMIN | `/plp-anchor/` |
| Borrower | borrower@credinnov.com | BORROWER | `/plp-borrower/` |

## PLP login / network error

Browsers must **not** call `http://localhost:8180` from the public site. Docker UIs mount `frontend/packages/*/docker/env-config.js`, which must use **`/plp-api`** when the hostname is `credinnov-sandbox.senseitech.com` (requires nginx `location /plp-api/`).

After updating env-config, restart PLP UI containers (no rebuild required):

```bash
docker compose -f docker-compose.yml -f docker-compose.ui.yml up -d platform-ui anchor-portal borrower-portal
```

Test API via nginx:

```bash
curl -s -X POST http://127.0.0.1/plp-api/api/v1/auth/login \
  -H 'Host: credinnov-sandbox.senseitech.com' \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@credinnov.com","password":"Bltest@123"}'
```

## PayU redirect to localhost (invoice discounting from LOS)

PayU **surl/furl** for invoice discounting are built by **PLP `lending-service`** (`PLP_PUBLIC_API_BASE_URL`), not LOS. If the browser redirects to `http://localhost:8180/api/v1/webhooks/payments/payu/success`, PLP is still using the local Docker default.

On EC2, after pulling PLP `credinnov`:

```bash
cd /vol/PLP-APP
# Option A — sandbox compose override (recommended)
docker compose -f docker-compose.yml -f docker-compose.sandbox.yml -f docker-compose.ui.yml up -d --build lending-service

# Option B — persist in .env (copy from .env.example)
grep PLP_PUBLIC_API_BASE_URL .env || cat >> .env <<'EOF'
PLP_PUBLIC_API_BASE_URL=http://credinnov-sandbox.senseitech.com/plp-api
PLP_BORROWER_UI_URL=http://credinnov-sandbox.senseitech.com/plp-borrower
LOS_BORROWER_UI_URL=http://credinnov-sandbox.senseitech.com/los/borrower
EOF
docker compose -f docker-compose.yml -f docker-compose.ui.yml up -d --build lending-service
```

Verify inside the container:

```bash
docker exec plp-lending printenv PLP_PUBLIC_API_BASE_URL
# expect: http://credinnov-sandbox.senseitech.com/plp-api
```

**LOS term-loan PayU** uses `LOS_PUBLIC_API_BASE_URL` on `los_core` (already defaulted in `docker-compose.prod.yml` to `http://credinnov-sandbox.senseitech.com/los`).

Whitelist in PayU dashboard:

- `http://credinnov-sandbox.senseitech.com/plp-api/api/v1/webhooks/payments/payu/success`
- `http://credinnov-sandbox.senseitech.com/plp-api/api/v1/webhooks/payments/payu/failure`
- `http://credinnov-sandbox.senseitech.com/los/api/v1/webhooks/los-payments/payu/success`
- `http://credinnov-sandbox.senseitech.com/los/api/v1/webhooks/los-payments/payu/failure`

## Deploy on EC2

```bash
# LOS
cd /vol/LOS_APP && git fetch && git checkout credinnov && git pull origin credinnov
docker compose -f docker-compose.prod.yml up -d --build los-core ui-service

# PLP
cd /vol/PLP-APP && git fetch && git checkout credinnov && git pull origin credinnov
docker compose -f docker-compose.yml -f docker-compose.sandbox.yml -f docker-compose.ui.yml up -d --build
```

Flyway runs migrations on service startup. To re-run on existing DB, restart `los_core` and `plp-iam` (or run SQL manually if migrations already applied).

## LOS → PLP anchor sync (503 on `/integrations/los/anchors`)

LOS logs in to PLP, then calls **`POST /api/v1/integrations/los/anchors`**, which the gateway routes to **`program-service`**.

| Symptom | Cause |
|---------|--------|
| Login 200, anchor sync **503** | `plp-program` not registered in Eureka or still starting |
| LOS uses `admin@plp.com` | Set `PLP_INTEGRATION_EMAIL=admin@credinnov.com` in `.env.prod` and restart `los_core` |

On EC2:

```bash
# PLP program service must be up
docker ps --filter name=plp-program
docker logs plp-program --tail 80
curl -s http://127.0.0.1:8182/actuator/health

# Direct anchor API test (after PLP login token)
TOKEN=$(curl -s -X POST http://127.0.0.1:8180/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@credinnov.com","password":"Bltest@123"}' | jq -r .accessToken)
curl -s -X POST http://127.0.0.1:8180/api/v1/integrations/los/anchors \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"sourceSystem":"LOS","losAnchorId":"00000000-0000-0000-0000-000000000099","anchor":{"name":"Test","code":"T-ANC-01","pan":"AAAAA0000A"}}'

# LOS must reach PLP gateway from inside the los_core container.
cd /vol/LOS_APP
# Preferred on EC2 (same Docker host as PLP):
docker compose -f docker-compose.prod.yml -f docker-compose.plp-integration.yml up -d --build los-core

# Verify from inside los_core (should return {"status":"UP"}):
docker exec los_core wget -qO- http://api-gateway:8080/actuator/health

# If plp_plp-net is missing: cd /vol/PLP-APP && docker compose up -d first, then docker network ls | grep plp
```

## Encore LMS (LOS → remote API)

LOS calls the Encore **webservices** API under `/credinnov-encore-server/...`, not the browser UI at `/encore-client/`. On Credinnov EC2 the team server is proxied as `/credinnov-encore-server/` → `127.0.0.1:8091`.

In `/vol/LOS_APP/.env.prod` (credentials unchanged):

```bash
ENCORE_BASE_URL=http://credinnov-sandbox.senseitech.com/credinnov-encore-server/
ENCORE_API_USERNAME=admin
ENCORE_API_PASSWORD=password1
```

Restart after edit (recreate container so env is reloaded):

```bash
docker compose -f docker-compose.prod.yml up -d --force-recreate --no-deps los-core
docker exec los_core printenv | grep ENCORE
docker logs los_core 2>&1 | grep "LOS Encore LMS client configured"
```

Expect `ENCORE_API_USERNAME=admin`, `ENCORE_API_PASSWORD=password1`, and startup log `apiUsername=admin`.

If LOS gets **401 Bad credentials** but PLP works, check `docker exec los_core printenv | grep ENCORE`. If you still see **`vuser`**, fix `.env.prod` on the server and recreate:

```bash
cd /vol/LOS_APP
sed -i 's/^ENCORE_API_USERNAME=.*/ENCORE_API_USERNAME=admin/' .env.prod
sed -i 's/^ENCORE_API_PASSWORD=.*/ENCORE_API_PASSWORD=password1/' .env.prod
grep ENCORE .env.prod
git pull origin credinnov   # compose now hardcodes admin/password1 in environment:
docker compose -f docker-compose.prod.yml up -d --force-recreate --no-deps los-core
docker exec los_core printenv | grep ENCORE
```

`docker-compose.prod.yml` sets `ENCORE_API_USERNAME=admin` in the `environment:` block so it overrides `.env.prod` even when that file still has `vuser`.

Smoke test from `los_core`:

```bash
docker exec los_core wget -qO- --user=admin --password=password1 \
  "http://credinnov-sandbox.senseitech.com/credinnov-encore-server/webservices/loans/accounts/findBankWorkingDate"
```

Public equivalent: `http://credinnov-sandbox.senseitech.com/credinnov-encore-server/`

### Repayment schedule & SOA (REST token)

Some Encore accounts return **HTTP 500** from `findSummaries` when `preclosureFeeRate` is null on the server (Encore bug for older accounts). LOS falls back to **`GET api/loan-od-accounts/{accountId}`**, which requires an **`X-Auth-Token`** header (same as the Encore UI), not Basic auth.

Set in `/vol/LOS_APP/.env.prod`:

```bash
ENCORE_REST_AUTH_TOKEN=<token-from-encore-ui>
```

**How to obtain the token:** log in to [Encore LMS UI](http://credinnov-sandbox.senseitech.com/encore-client/) as `admin` / `password1`, open DevTools → Network, trigger any API call (e.g. open a loan account), copy the **`X-Auth-Token`** request header value.

Restart LOS after setting:

```bash
docker compose -f docker-compose.prod.yml up -d --force-recreate --no-deps los-core
docker exec los_core printenv | grep ENCORE_REST_AUTH_TOKEN
docker logs los_core 2>&1 | grep restAuthTokenConfigured
```

Expect startup log `restAuthTokenConfigured=true`. Without it, schedule/SOA sync logs **401** on `api/loan-od-accounts/*` and falls back to the broken `findSummaries` webservice.

Smoke test (replace token and account id):

```bash
curl -s "http://credinnov-sandbox.senseitech.com/credinnov-encore-server/api/loan-od-accounts/000000025591" \
  -H "X-Auth-Token: YOUR_TOKEN" -H "Accept: application/json" | head -c 500
```

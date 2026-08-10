# Host nginx — LOS & PLP on Credinnov EC2

Host **port 80** is owned by system nginx (`credinnov-sandbox.senseitech.com`). Docker apps listen on localhost ports; nginx exposes them by URL path **without changing** existing team locations (`/`, `/credinnov-encore-server/`, etc.).

Segregated workspace layout:

```
BT-LOS-WORKSPACE/
├── LOS/          discovery, los-core, notification, ui-service
├── PLP/          backends + 3 UIs
└── infra/
    ├── los/      docker compose for LOS
    ├── plp/      docker compose for PLP
    └── deploy/nginx/   ← these snippets
```

## Port map

| URL path | Proxy to | App |
|----------|----------|-----|
| `/los/` | `127.0.0.1:8080/los/` | LOS UI + `/los/api/` → los-core |
| `/plp/` | `127.0.0.1:3100` | PLP platform UI |
| `/plp-anchor/` | `127.0.0.1:3200` | PLP anchor portal |
| `/plp-borrower/` | `127.0.0.1:3300` (rewrite to `/plp/`) | PLP borrower portal |
| `/plp-api/` | `127.0.0.1:8180/` | PLP API gateway |

## One-time nginx setup

### 1. Copy snippets

```bash
cd /path/to/BT-LOS-WORKSPACE/infra
sudo cp deploy/nginx/los-host-locations.conf /etc/nginx/snippets/los-host-locations.conf
sudo cp deploy/nginx/plp-host-locations.conf /etc/nginx/snippets/plp-host-locations.conf
```

### 2. Edit the existing site (additive only)

Inside the **`server { }`** block that already has `server_name credinnov-sandbox.senseitech.com;`, add:

```nginx
    include snippets/los-host-locations.conf;
    include snippets/plp-host-locations.conf;
```

Do **not** delete or change existing Encore or static locations.

### 3. Reload nginx

```bash
sudo nginx -t && sudo systemctl reload nginx
```

### 4. PLP API URL in the browser

Set on all three PLP UI `env-config.js` files:

```javascript
window.__ENV__ = { VITE_API_BASE_URL: '/plp-api' };
```

Paths in segregated layout:

- `PLP/platform-ui/docker/env-config.js`
- `PLP/anchor-portal/docker/env-config.js`
- `PLP/borrower-portal/docker/env-config.js`

Or use `infra/plp/ui-services/env-config.js` for compose-based deploys.

## Verify

```bash
curl -s -o /dev/null -w "LOS /los/ %{http_code}\n" -H "Host: credinnov-sandbox.senseitech.com" http://127.0.0.1/los/
curl -s -o /dev/null -w "PLP /plp/ %{http_code}\n" -H "Host: credinnov-sandbox.senseitech.com" http://127.0.0.1/plp/
curl -s -o /dev/null -w "PLP API %{http_code}\n" -H "Host: credinnov-sandbox.senseitech.com" http://127.0.0.1/plp-api/actuator/health
```

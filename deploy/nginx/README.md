# Host nginx — LOS & PLP on Credinnov EC2

Host **port 80** is owned by system nginx (`credinnov-sandbox.senseitech.com`). Docker apps listen on localhost ports; nginx exposes them by URL path **without changing** existing team locations (`/`, `/credinnov-encore-server/`, etc.).

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
cd /vol/LOS_APP
sudo cp deploy/nginx/los-host-locations.conf /etc/nginx/snippets/los-host-locations.conf
sudo cp deploy/nginx/plp-host-locations.conf /etc/nginx/snippets/plp-host-locations.conf
```

### 2. Edit the existing site (additive only)

```bash
sudo nano /etc/nginx/sites-available/default
```

Inside the **`server { }`** block that already has `server_name credinnov-sandbox.senseitech.com;`, add these two lines **anywhere before the closing `}`** (e.g. after the `/credinnov-encore-server/` block):

```nginx
    include snippets/los-host-locations.conf;
    include snippets/plp-host-locations.conf;
```

Do **not** delete or change:

- `location / { try_files ... }`
- `location /credinnov-encore-server/ { ... }`

### 3. Reload nginx

```bash
sudo nginx -t && sudo systemctl reload nginx
```

### 4. PLP API URL in the browser

Edit on EC2 (all three files), set:

```javascript
window.__ENV__ = { VITE_API_BASE_URL: '/plp-api' };
```

- `/vol/PLP-APP/frontend/packages/platform-ui/docker/env-config.js`
- `/vol/PLP-APP/frontend/packages/anchor-portal/docker/env-config.js`
- `/vol/PLP-APP/frontend/packages/borrower-portal/docker/env-config.js`

Restart PLP UIs:

```bash
cd /vol/PLP-APP
docker compose -f docker-compose.yml -f docker-compose.ui.yml up -d platform-ui anchor-portal borrower-portal
```

Ensure PLP gateway is healthy first:

```bash
curl -s http://127.0.0.1:8180/actuator/health
```

## Verify

```bash
curl -s -o /dev/null -w "LOS /los/ %{http_code}\n" -H "Host: credinnov-sandbox.senseitech.com" http://127.0.0.1/los/
curl -s -o /dev/null -w "PLP /plp/ %{http_code}\n" -H "Host: credinnov-sandbox.senseitech.com" http://127.0.0.1/plp/
curl -s -o /dev/null -w "PLP anchor %{http_code}\n" -H "Host: credinnov-sandbox.senseitech.com" http://127.0.0.1/plp-anchor/
curl -s -o /dev/null -w "PLP API %{http_code}\n" -H "Host: credinnov-sandbox.senseitech.com" http://127.0.0.1/plp-api/actuator/health
```

## Browser URLs

| App | URL |
|-----|-----|
| Encore LMS (team) | `http://credinnov-sandbox.senseitech.com/encore-client/` |
| LOS | `http://credinnov-sandbox.senseitech.com/los/` |
| PLP platform | `http://credinnov-sandbox.senseitech.com/plp/` |
| PLP anchor | `http://credinnov-sandbox.senseitech.com/plp-anchor/` |
| PLP borrower | `http://credinnov-sandbox.senseitech.com/plp-borrower/` |

Security group: public **80** (and **443** if TLS added). Ports 8080, 3100–3300, 8180 stay on localhost only.

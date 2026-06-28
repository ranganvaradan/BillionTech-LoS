# LOS AWS Lightsail Docker Deployment Pack

This pack assumes:
- Backend service: `services/los-core-service`
- Backend port: `8083`
- Active frontend: `ui-service` using React/Vite
- Postgres runs in Docker
- Production document storage is filesystem volume `/data/documents`

## 1. Copy files into app root

On Windows PowerShell:

```powershell
cd D:\CurrentAug032025\Platform\los-app

copy AWS_RECREATED\docker-compose.prod.yml .
copy AWS_RECREATED\.env.prod.template .
copy AWS_RECREATED\los-core-service.Dockerfile.prod services\los-core-service\Dockerfile.prod
copy AWS_RECREATED\ui-service.Dockerfile.prod ui-service\Dockerfile.prod
copy AWS_RECREATED\nginx\default.conf ui-service\nginx.conf
xcopy AWS_RECREATED\scripts scripts /E /I /Y
copy .env.prod.template .env.prod
notepad .env.prod
```

Change at least:
- `POSTGRES_PASSWORD`
- `JWT_SECRET`

## 2. Local Docker test before AWS

```powershell
docker compose -f docker-compose.prod.yml --env-file .env.prod up --build -d
```

Open:

```text
http://localhost
```

Health check:

```powershell
Invoke-WebRequest http://localhost/actuator/health
```

Stop:

```powershell
docker compose -f docker-compose.prod.yml down
```

## 3. Create Lightsail instance

Recommended minimum:
- Ubuntu 22.04/24.04
- 2 GB RAM minimum, 4 GB better for builds
- Attach a Static IP
- Open inbound ports 22 and 80 initially

## 4. Install Docker on Lightsail

```bash
sudo apt update
sudo apt install -y ca-certificates curl gnupg git unzip
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu
newgrp docker
docker --version
docker compose version
```

## 5. Upload project to Lightsail

From Windows PowerShell:

```powershell
cd D:\CurrentAug032025\Platform
scp -r .\los-app ubuntu@YOUR_STATIC_IP:/home/ubuntu/
```

On Lightsail:

```bash
cd /home/ubuntu/los-app
chmod +x scripts/*.sh
```

## 6. Deploy

```bash
./scripts/deploy-lightsail.sh
```

Check:

```bash
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f los-core
docker compose -f docker-compose.prod.yml logs -f ui-service
```

Open:

```text
http://YOUR_STATIC_IP
```

## 7. Reset deployment if needed

Warning: this deletes database volume.

```bash
docker compose -f docker-compose.prod.yml down -v
docker compose -f docker-compose.prod.yml up --build -d
```

## 8. Backup database

```bash
./scripts/backup-db.sh
```

## 9. Important production notes

- Keep frontend API base as `/api`; Nginx proxies `/api/` to backend service `los-core:8083`.
- Do not deploy the old `frontend` folder unless you intentionally want the legacy UI.
- Document uploads persist in the named Docker volume `los_documents`.
- Postgres data persists in `los_postgres_data`.
- Add domain + SSL after basic IP deployment works.

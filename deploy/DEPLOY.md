# Guild Points — Production Deploy Guide

Split deploy: **Vercel** (frontend) + **Hostinger VPS KVM 2** (backend + PostgreSQL) with **Coolify** for backend CI/CD.

Replace `seudominio.com` with your domain everywhere below.

## Architecture

| Host | Service |
|------|---------|
| `app.seudominio.com` | React SPA (Vercel) |
| `api.seudominio.com` | Spring Boot + STOMP `/ws` + uploads (VPS) |

---

## 1. VPS — Hostinger KVM 2 (São Paulo)

1. Order **KVM 2** (2 vCPU, 8 GB RAM).
2. **Location:** São Paulo, Brasil (latency for BR users).
3. **OS:** Ubuntu 24.04 LTS — tab **SO simples**.
4. **Panel:** Coolify — tab **Painel de controle** (install via Hostinger template or [coolify.io](https://coolify.io/docs/get-started/installation)).
5. Note the VPS **public IP** for DNS.

Access day-to-day:

- Coolify web UI (deploy, logs, env vars, SSL).
- SSH from Windows: `ssh root@<vps-ip>` (PowerShell or PuTTY).
- Hostinger Web Terminal in hPanel.

Do **not** use Windows RDP — Linux + Docker is the intended stack.

---

## 2. DNS

At your domain registrar:

| Record | Type | Value |
|--------|------|-------|
| `app` | CNAME | Value shown by Vercel when adding custom domain |
| `api` | A | VPS public IP |

Wait for propagation (often 5–30 min). SSL is automatic on Vercel and Coolify (Let's Encrypt).

---

## 3. Coolify — Backend + PostgreSQL

### PostgreSQL

1. Coolify → **+ New** → **Database** → **PostgreSQL 16**.
2. Database name: `guild_points`, user: `guild`, strong password.
3. Note internal hostname (e.g. `postgres-xxxx`).

### Backend application

1. **+ New** → **Application** → **Public Git** → your GitHub repo.
2. Branch: `main`.
3. **Build pack:** Dockerfile.
4. **Base directory:** `backend`.
5. **Domains:** `api.seudominio.com` → enable HTTPS.
6. **Persistent storage:** mount volume at `/data/uploads`.
7. **Environment variables** — copy from [`deploy/coolify.env.example`](coolify.env.example):
   - `SPRING_PROFILES_ACTIVE=prod`
   - `SPRING_DATASOURCE_URL=jdbc:postgresql://<postgres-host>:5432/guild_points`
   - `JWT_SECRET` — generate: `openssl rand -base64 48`
   - `CORS_ORIGINS=https://app.seudominio.com`
8. **Auto Deploy:** enable webhook on push to `main`.

Alternative reference stack: [`deploy/docker-compose.prod.yml`](docker-compose.prod.yml).

### WebSocket

Coolify/Caddy proxies WebSocket upgrades by default. After deploy, verify `https://api.seudominio.com/ws/info` returns SockJS info JSON.

---

## 4. Vercel — Frontend

1. [vercel.com](https://vercel.com) → **Add New Project** → import GitHub repo.
2. **Root Directory:** `frontend`.
3. Framework: Vite (auto-detected; [`frontend/vercel.json`](../frontend/vercel.json) included).
4. **Environment variables** (Production):

   | Variable | Value |
   |----------|-------|
   | `VITE_API_BASE_URL` | `https://api.seudominio.com/api` |
   | `VITE_WS_BASE_URL` | `https://api.seudominio.com` |

5. **Domains:** add `app.seudominio.com`.
6. Production branch: `main` → auto deploy on push.

Local dev: leave env vars unset; Vite proxy handles `/api` and `/ws`.

---

## 5. CI (GitHub Actions)

[`.github/workflows/ci.yml`](../.github/workflows/ci.yml) runs tests on push/PR. Coolify and Vercel deploy independently on push to `main` after merge.

---

## 6. Validation

Run after DNS and both deploys are live:

```powershell
# Windows
.\deploy\validate-prod.ps1 -ApiHost api.seudominio.com -AppHost app.seudominio.com
```

```bash
# Linux / macOS / VPS
./deploy/validate-prod.sh api.seudominio.com app.seudominio.com
```

Manual checks:

1. `https://app.seudominio.com` loads SPA.
2. Register/login works.
3. DevTools → Network → WS connects to `api.seudominio.com/ws`.
4. Auction bids update in real time for multiple users.
5. Upload item image → survives backend container restart.
6. Push to `main` → Vercel + Coolify redeploy without SSH.

---

## 7. Secrets checklist

Never commit:

- `JWT_SECRET`
- `POSTGRES_PASSWORD` / `SPRING_DATASOURCE_PASSWORD`
- `DISCORD_CLIENT_SECRET`

Generate JWT secret:

```bash
openssl rand -base64 48
```

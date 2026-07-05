# Deployment

How InsuranceCRM's two apps get from a merge on GitHub to running containers, and what the
Oracle Cloud deployment needs from this side.

> **Not yet tested against a real Docker daemon** — Docker wasn't available in the environment
> these files were written in. Before relying on this in production, run a local `docker build`
> (see below) and fix anything that surfaces.

## Architecture

```
GitHub (push to main/master)
  → GitHub Actions builds Docker image
  → pushes to Docker Hub (nawaz027/insurancecrm, nawaz027/insurancecrm-fe)

Oracle Cloud VM
  → docker compose pull && docker compose up -d
  → frontend (nginx, port 80) ──proxies /api/*──→ backend (Spring Boot, port 8081)
                                                         │
                                                         ▼
                                              MongoDB Atlas (free M0 cluster)
```

The frontend's built JS always calls a **relative** `/api/...` path (see `src/lib/axios.ts`) —
there's no build-time backend URL to configure. That means nginx (in the frontend container)
must reverse-proxy `/api/` to the backend container — already set up in `insurancecrm-fe/nginx.conf`,
proxying to a service literally named `backend` (matching the `docker-compose.yml` service name).
If the Oracle setup runs these containers under different service names or on different hosts,
that proxy target needs updating to match.

## Images

- **Backend** (`Dockerfile`, this repo): multi-stage — `eclipse-temurin:21-jdk-jammy` runs
  `./gradlew bootJar -x test` (tests are skipped here since there's no MongoDB reachable during
  the image build — CI runs the full test suite separately, before this step), then the jar is
  copied into a slim `eclipse-temurin:21-jre-jammy` runtime image, running as a non-root user.
- **Frontend** (`Dockerfile`, in `insurancecrm-fe`): multi-stage — `node:20-alpine` runs
  `npm ci && npm run build`, then the static `dist/` output is served by `nginx:1.27-alpine`.

## CI/CD (GitHub Actions)

`.github/workflows/docker-publish.yml` in **both** repos builds and pushes to Docker Hub on every
push to `main` or `master` (this repo's current default branch is `master` — rename it to `main`
if you'd rather match convention, the workflow triggers on either).

**One-time setup**, in each repo's GitHub Settings → Secrets and variables → Actions:

| Secret | Value |
|---|---|
| `DOCKERHUB_USERNAME` | Your Docker Hub username |
| `DOCKERHUB_TOKEN` | A Docker Hub [access token](https://app.docker.com/settings/personal-access-tokens) (not your password) — needs Read & Write |

Each push produces two tags: `:latest` and `:<commit-sha>` (so you can pin a specific version in
`docker-compose.yml` via `TAG=<sha>` if `:latest` ever needs rolling back).

## MongoDB Atlas setup

1. Create a free **M0** cluster (512MB storage, shared RAM/vCPU, 3-node replica set included).
2. **Network Access** → add the Oracle VM's public IP (M0 has no VPC peering, so this is the
   only way in).
3. **Database Access** → create a user with read/write on the `insurancecrm` database.
4. Copy the connection string (`mongodb+srv://...`) — this is `MONGODB_URI`.

### Backups

M0's replication protects against a node dying, but a bad bulk-update or a buggy migration
replicates everywhere just as fast — Atlas doesn't offer point-in-time restore below the paid
M10 tier. `scripts/backup-mongo.sh` runs `mongodump` via Docker (no local install needed) and
prunes anything older than `RETENTION_DAYS`. Wire it up as a cron job on the VM:

```
0 2 * * * MONGODB_URI="mongodb+srv://..." BACKUP_DIR="/opt/insurancecrm/backups" /opt/insurancecrm/scripts/backup-mongo.sh >> /var/log/insurancecrm-backup.log 2>&1
```

The script leaves an "upload elsewhere" step unconfigured at the bottom — backups sitting only on
the same VM they're backing up won't survive that VM being lost. Point it at OCI Object Storage
(or wherever) once that's decided.

## Required environment variables (backend container)

| Variable | Required | Notes |
|---|---|---|
| `JWT_SECRET` | **Yes** | 32+ random characters. The app refuses to start without it — there is no baked-in default since the source is public. Generate with `openssl rand -base64 48`. |
| `MONGODB_URI` | **Yes** | Full Atlas connection string, including the database name. |

## Running via docker-compose

`docker-compose.yml` in this repo pulls the published images (doesn't rebuild from source):

```bash
# .env alongside docker-compose.yml:
#   DOCKERHUB_USERNAME=nawaz027
#   JWT_SECRET=...
#   MONGODB_URI=...
#   TAG=latest

docker compose pull
docker compose up -d
```

This is the piece that's the Oracle Cloud teammate's to run/adapt — VM sizing, firewall/security
list rules (80/443 in, plus whatever's needed for SSH), and TLS termination (e.g. a Caddy or
nginx reverse proxy in front with Let's Encrypt, since neither app container currently handles
HTTPS) aren't covered here.

## Verifying a build locally (not yet done — see caveat above)

```bash
docker build -t insurancecrm-backend .
docker run --rm -e JWT_SECRET=test-secret-please-replace -e MONGODB_URI="mongodb://host.docker.internal:27017/test" -p 8081:8081 insurancecrm-backend
```

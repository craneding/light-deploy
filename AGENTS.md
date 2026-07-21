# AGENTS.md

## Architecture

Two independent packages (no monorepo tooling):

- **`backend/`** - Spring Boot 3.2.4 (Java 17, Maven). GitLab OAuth2 + JWT auth, MyBatis-Plus + MySQL, WebSocket for live deploy logs, JSch for SSH, rsync for file sync.
- **`frontend/`** - Vue 3 + TypeScript + Vite 8 + Element Plus + Pinia. Proxies `/api` to backend via Vite dev server.
- **`db/schema.sql`** - MySQL schema (must be imported before backend starts).

### Key entrypoints

- Backend: `backend/src/main/java/com/lightdeploy/backend/BackendApplication.java`
- Deploy engine (core logic): `backend/src/main/java/com/lightdeploy/backend/service/DeployEngineService.java` - runs async, clones repo, builds, rsyncs, runs remote scripts
- Security config: `backend/src/main/java/com/lightdeploy/backend/security/SecurityConfig.java`
- OAuth2 success handler: redirects to `{app.frontend-url}/login/success?token=...` (configurable via `APP_FRONTEND_URL` env var)
- WebSocket config: `/ws/deploy` endpoint (no auth, CORS `*`)
- Frontend router: `frontend/src/router/index.ts`
- Frontend HTTP client: `frontend/src/utils/request.ts` (axios, JWT bearer token injection)

## Commands

### Backend

```bash
cd backend
./mvnw clean spring-boot:run          # dev server (port 8080, context-path /api)
./mvnw clean package -DskipTests      # build jar → backend/target/
./mvnw test                           # run tests (only BackendApplicationTests.java exists)
```

Maven wrapper (`mvnw`) is included; prefer it over system `mvn`.

### Frontend

```bash
cd frontend
npm install
npm run dev        # Vite dev server on port 3000, auto-opens browser
npm run build      # vue-tsc -b && vite build → frontend/dist/
```

No separate lint, format, or typecheck commands exist. `npm run build` is the only verification step and it includes TypeScript checking via `vue-tsc -b`.

### Database

```bash
mysql -u root -p < db/schema.sql     # creates light_deploy database + tables
```

Default credentials in `application.yml`: root/123456, database `light_deploy`.

## Gotchas

- **OAuth frontend redirect**: `OAuth2AuthenticationSuccessHandler.java:41` redirects to `{app.frontend-url}/login/success`. Default is `http://localhost:3000`, override via `APP_FRONTEND_URL` env var for Docker.
- **JWT secret is hardcoded** in `JwtUtils.java:15` - must be externalized for production.
- **application.yml uses env var placeholders** (e.g. `${GITLAB_URL:...}`, `${GITLAB_CLIENT_ID:...}`). Dev defaults are baked in; override via env vars in Docker.
- **Deploy engine uses `user.dir`** for workspace (`.workspace/`) and artifacts (`.artifacts/`). These paths depend on where the backend process starts. In Docker they land inside the container - bind-mount or change config if persistence is needed.
- **`@EnableAsync`** on `BackendApplication` - deploy tasks execute asynchronously via `DeployEngineService.executeDeploy()`.
- **MyBatis-Plus SQL logging** is enabled (`StdOutImpl` in `application.yml`). Remove for production.
- **Logical delete** is configured globally (`deleted` field, 0/1). All entity queries auto-filter deleted records.
- **Frontend build output** goes to `frontend/dist/` - serve via Nginx in production.
- **Backend `context-path` is `/api`** - all REST endpoints are under `/api/*`.
- **Forward headers**: Docker deployment must set `server.forward-headers-strategy=framework` (via `JAVA_OPTS` or env) for OAuth `{baseUrl}` to resolve correctly behind Nginx. Already set in `docker-compose.yml`.

## Docker

### Build & save backend image on dev machine

```bash
cd <project-root>
docker build -t light-deploy-backend ./backend
docker save light-deploy-backend | gzip > light-deploy-backend.tar.gz
# Copy tar.gz to target server
```

### On target server

```bash
docker load < light-deploy-backend.tar.gz

# Create .env file (or pass inline)
cat > .env <<EOF
MYSQL_HOST=your-mysql-host
MYSQL_PASSWORD=your-mysql-password
GITLAB_CLIENT_ID=xxx
GITLAB_CLIENT_SECRET=xxx
GITLAB_URL=https://gitlab.your-company.com    # omit for gitlab.com
APP_FRONTEND_URL=http://your-domain.com       # override for OAuth redirect
EOF

docker compose up -d
docker compose logs -f backend
docker compose down -v
```

### Required env vars

| Variable | Purpose |
|---|---|
| `MYSQL_HOST` | MySQL server hostname/IP |
| `MYSQL_PASSWORD` | MySQL password |
| `GITLAB_CLIENT_ID` | GitLab OAuth application ID |
| `GITLAB_CLIENT_SECRET` | GitLab OAuth application secret |
| `GITLAB_URL` | GitLab instance URL (default `https://gitlab.com`) |
| `APP_FRONTEND_URL` | Frontend URL for OAuth redirect (default `http://localhost:3000`) |

### Register OAuth redirect URI in GitLab

```
http://your-domain.com/api/login/oauth2/code/gitlab
```

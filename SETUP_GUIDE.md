# Quick Setup Guide

For the full feature list, architecture, and known limitations, see [README.md](README.md).
For what was actually broken and fixed in this codebase, see
[docs/VERIFICATION_REPORT.md](docs/VERIFICATION_REPORT.md). This file is just the fast path
to running it locally.

## Fastest path: Docker Compose

```bash
cp backend/.env.example backend/.env      # edit as needed
cp frontend/.env.example frontend/.env    # edit as needed
docker compose up --build
```

- Frontend: http://localhost:3000
- Backend: http://localhost:8080
- Swagger: http://localhost:8080/swagger-ui.html
- Actuator: http://localhost:8080/actuator/health

To run the full end-to-end check (build, tests, real Postgres/Redis/Kafka, feed cache
hit/miss, a Kafka-driven notification, a live WebSocket push, Prometheus metrics):

```bash
bash scripts/verify.sh
```

## Local development (without Docker for the app itself)

```bash
# 1. Start just the infrastructure
docker compose up -d postgres redis kafka zookeeper

# 2. Run backend
cd backend
mvn spring-boot:run

# 3. Run frontend (separate terminal)
cd frontend
npm install
npm run dev
```

## Common Issues

1. **Port already in use** - stop conflicting services or change ports in `docker-compose.yml`.
2. **Database connection failed** - wait for PostgreSQL's healthcheck to pass; check
   credentials in `.env`.
3. **Frontend can't reach the backend when built via Docker** - Vite bakes
   `VITE_API_BASE_URL` in at *build* time. Setting it as a container `environment:` variable
   on `frontend` does nothing; change `docker-compose.yml`'s `build.args` instead and rebuild.
4. **Redis connection fails** - `REDIS_SSL_ENABLED` must match your target: `false` for a
   plain local/Docker Redis, `true` for a TLS-only managed Redis like Upstash. It does not
   auto-detect.

# Social Media Platform

A social media platform built with Spring Boot and React + TypeScript: posts, follows, a
chronological following-based feed, Redis-backed caching, Kafka-driven notifications, and
authenticated real-time WebSocket delivery.

This README describes what is actually implemented and verified. See
[docs/VERIFICATION_REPORT.md](docs/VERIFICATION_REPORT.md) for the full audit trail: what was
broken, what was fixed, and exactly how each claim below was checked against real
PostgreSQL/Redis/Kafka, not mocks.

## Table of Contents

- [Overview](#overview)
- [Tech Stack](#tech-stack)
- [Features](#features)
- [Architecture](#architecture)
- [Getting Started](#getting-started)
- [API Documentation](#api-documentation)
- [Security](#security)
- [Testing](#testing)
- [Deployment](#deployment)
- [Observability](#observability)
- [Known Limitations](#known-limitations)
- [Contributing](#contributing)

## Overview

This platform enables users to:
- Create, edit, and delete posts (image posts use a URL string, not a server-side upload)
- Like, comment, and share posts
- Follow/unfollow other users
- Receive real-time notifications via an authenticated WebSocket/STOMP connection
- View a chronological feed of posts from people they follow

## Tech Stack

### Backend
- **Java 17** with Spring Boot 3.2
- **PostgreSQL** - primary database, schema managed by Flyway migrations
- **Redis** - feed caching (Spring Cache abstraction, manually instrumented for hit/miss metrics)
- **Kafka** - a single `notification-events` topic carries all notification types (new
  follower, post liked, commented, shared) from producer to consumer
- **WebSocket (STOMP over SockJS)** - real-time notifications, authenticated via JWT passed
  as a STOMP CONNECT header (see [Security](#security))
- **Spring Security** - JWT (HS256) + optional Google OAuth2 login
- **Swagger/OpenAPI** - API documentation
- **Spring Boot Actuator + Micrometer/Prometheus** - health, metrics, custom counters

### Frontend
- **React 18** with TypeScript
- **Redux Toolkit** - state management
- **React Query** - data fetching and caching
- **Material UI** - component library
- **Axios** - HTTP client with a refresh-token interceptor
- **Vite** - build tool
- **@stomp/stompjs + sockjs-client** - WebSocket notifications client

### DevOps
- **Docker + Docker Compose** - full local stack (Postgres, Redis, Kafka, backend, frontend)
- **GitHub Actions** - backend build+test (incl. Testcontainers integration tests, which run
  for real on GitHub-hosted runners since they have Docker), frontend build+test, and a
  full docker-compose end-to-end verification job (`scripts/verify.sh`)
- **AWS ECS/S3/CloudFront** - **not implemented**. See [Known Limitations](#known-limitations).

## Features

### Authentication & Authorization
- Email/password registration and login, JWT access + refresh tokens
- Optional Google OAuth2 login (code is real - JWT is issued on successful OAuth2 login -
  but requires real `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET` to exercise; not runtime-verified
  in CI since that would require live Google credentials)
- Role-based access control (USER, ADMIN)
- Rate limiting on auth/posts/feed endpoints (Resilience4j `@RateLimiter`, actually wired to
  those endpoints, not just a dependency)

### Posts
- Create posts with text and an optional image URL
- Edit/delete own posts (ownership enforced, returns 403 otherwise)
- Like/unlike posts, idempotent under concurrent duplicate requests (DB unique constraint on
  `(post_id, user_id)`, verified by `ConcurrencyIntegrationTest`)
- Comment on posts, share posts
- Pagination support

### Social Features
- Follow/unfollow users, idempotent under concurrent duplicate requests (DB unique constraint
  on `(follower_id, following_id)`, verified by `ConcurrencyIntegrationTest`)
- Followers/following lists
- Chronological feed of posts from followed users (plus your own) - this is **not** a ranked
  or ML-personalized feed, just "everyone you follow, newest first"

### Notifications
- New follower, post liked, post commented, post shared - all four flow through one Kafka
  topic/event type, consumed by a single `@KafkaListener`, persisted, then pushed live over
  WebSocket to the recipient if they're connected
- Unread count, mark-as-read / mark-all-as-read

### Performance
- Redis-backed feed cache (10-minute TTL), manually instrumented so hits/misses are real
  Prometheus counters (`feed.cache.hit` / `feed.cache.miss`), not just an unused annotation
- Cache is invalidated (`allEntries=true` on the `userFeed` cache) whenever the current user
  creates a post, follows, or unfollows - simple and correct, at the cost of evicting other
  users' unrelated cached feed pages too (a deliberate simplicity/consistency tradeoff, not a
  partial per-user invalidation scheme)
- Database indexing, HikariCP connection pooling, pagination

## Architecture

### System Architecture

```
┌─────────────┐
│   Client    │
│  (React)    │
└──────┬──────┘
       │
       ├─── HTTP/REST ────┐
       │                  │
       └─── WebSocket ────┤
                          │
                    ┌─────▼──────┐
                    │   Nginx    │
                    │  (Reverse  │
                    │   Proxy)   │
                    └─────┬──────┘
                          │
                    ┌─────▼────────┐
                    │  Spring Boot │
                    │   Backend    │
                    └──┬───┬───┬───┘
                       │   │   │
           ┌───────────┘   │   └──────────┐
           │               │              │
      ┌────▼────┐    ┌────▼────┐   ┌────▼────┐
      │  Redis  │    │ Postgres│   │  Kafka  │
      │ (Cache) │    │   (DB)  │   │ (Events)│
      └─────────┘    └─────────┘   └─────────┘
```

### Data Flow

1. **User Request** → Frontend → Backend API
2. **Authentication** → JWT validation → process request
3. **Database Query** → check Redis cache → query PostgreSQL on miss → populate cache
4. **Async Events** → publish to Kafka → `NotificationConsumer` persists a `Notification`
5. **Real-time Updates** → the same consumer pushes over WebSocket to the recipient's
   authenticated STOMP session, if connected

## Getting Started

### Prerequisites

- Java 17+
- Node.js 20+
- Docker and Docker Compose
- Maven 3.9+

### Local Development Setup

1. **Clone the repository**
```bash
git clone https://github.com/danishirfan21/Social-Media-Platform.git
cd Social-Media-Platform
```

2. **Set up backend**
```bash
cd backend
cp .env.example .env
# Edit .env with your configuration
mvn clean install
```

3. **Set up frontend**
```bash
cd frontend
cp .env.example .env
npm install
```

4. **Start services with Docker Compose**
```bash
docker compose up -d postgres redis kafka zookeeper
```

5. **Run backend**
```bash
cd backend
mvn spring-boot:run
```

6. **Run frontend**
```bash
cd frontend
npm run dev
```

The application will be available at:
- Frontend: http://localhost:3000
- Backend API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- Actuator: http://localhost:8080/actuator

### Docker Setup (Full Stack)

```bash
docker compose up --build
```

Or run the full verification (build, tests, stack startup, and a real end-to-end check of
every claim above) with:

```bash
bash scripts/verify.sh
```

**Note on the frontend and Docker:** Vite inlines `VITE_API_BASE_URL` into the JS bundle at
*build* time, not at container start time - `frontend/Dockerfile` takes it as a build `ARG`,
and `docker-compose.yml` supplies it via `build.args`. Setting an `environment:` variable on
the running frontend container does nothing; if you need a different backend URL, rebuild the
image with a different build arg.

## API Documentation

### Authentication

#### Register
```http
POST /api/auth/register
Content-Type: application/json

{
  "username": "john_doe",
  "email": "john@example.com",
  "password": "SecurePass123"
}
```

#### Login
```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "john@example.com",
  "password": "SecurePass123"
}
```

#### Response
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIs...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
  "tokenType": "Bearer",
  "expiresIn": 86400,
  "user": {
    "id": 1,
    "username": "john_doe",
    "email": "john@example.com"
  }
}
```

### Posts

#### Create Post
```http
POST /api/posts
Authorization: Bearer {token}
Content-Type: application/json

{
  "content": "Hello, world!",
  "imageUrl": "https://example.com/image.jpg"
}
```

#### Get Feed
```http
GET /api/feed?page=0&size=20
Authorization: Bearer {token}
```

### Full API Documentation

Access complete API documentation at: http://localhost:8080/swagger-ui.html

## Security

### Implemented Security Measures

1. **Authentication**
   - JWT access + refresh tokens, **HS256/HMAC signed** (the secret in `JWT_SECRET`) - not
     RS256/RSA, despite what earlier versions of this README claimed. There is no RSA keypair
     anywhere in this codebase; correcting that claim rather than bolting on unused RSA
     infrastructure just to match old docs.
   - BCrypt password hashing
   - Optional Google OAuth2 login, issuing the same app JWTs on success
2. **Authorization**
   - Role-based access control
   - Resource ownership validation (post edit/delete, profile update, notification
     mark-as-read all return 403 via a dedicated `ForbiddenException`, not a generic 500)
3. **WebSocket authentication**
   - The `/ws` handshake itself is unauthenticated at the HTTP layer (browsers can't attach a
     Bearer header to a WebSocket upgrade), so the JWT is instead passed as a STOMP `CONNECT`
     header and validated by `WebSocketAuthChannelInterceptor`, which attaches the resolved
     user id as the STOMP session's Principal. Without this, `convertAndSendToUser(...)` has
     no session to route a message to - it's not just decorative config.
4. **Network Security**
   - CORS configuration
   - Rate limiting (Resilience4j, applied to real endpoints - see Features above)
   - No HTTPS enforcement in this codebase (TLS is expected to be terminated by whatever
     reverse proxy/host you deploy behind; nothing here rejects plain HTTP)
5. **Data Protection**
   - Bean Validation on request DTOs
   - SQL injection prevention via JPA/parameterized queries
   - Global exception handler no longer leaks raw exception messages on unexpected 500s
6. **Monitoring**
   - Spring Boot Actuator, health checks, Prometheus metrics

### Environment Variables

Create `.env` file based on `.env.example` (see `backend/.env.example` for the full list,
including comments on when `REDIS_SSL_ENABLED` needs to be true vs false):

```env
# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=socialmedia
DB_USER=postgres
DB_PASSWORD=<strong-password>

# JWT
JWT_SECRET=<256-bit-secret-key>
JWT_EXPIRATION=86400000

# OAuth2 (optional)
GOOGLE_CLIENT_ID=<your-client-id>
GOOGLE_CLIENT_SECRET=<your-client-secret>
FRONTEND_URL=http://localhost:3000
```

To actually test Google login: create OAuth 2.0 credentials in the
[Google Cloud Console](https://console.cloud.google.com/), and add
`http://localhost:8080/login/oauth2/code/google` (plus your real domain's equivalent for a
deployed instance) as an authorized redirect URI.

## Testing

### Backend Tests

```bash
cd backend
mvn test          # unit + controller-slice tests, no Docker required
mvn verify         # also runs Testcontainers integration tests - requires Docker
```

`ConcurrencyIntegrationTest` fires 10 simultaneous duplicate follow/like requests and asserts
exactly one row results in each case, backed by the database unique constraints, not just
JVM-level locking.

### Frontend Tests

```bash
cd frontend
npm test
npm run test:ui
```

### E2E Tests

The README previously claimed Cypress; the actual e2e spec (`frontend/e2e/happy-path.spec.ts`)
uses **Playwright** and the Cypress dependency was unused dead weight - it's been removed.

```bash
cd frontend
npm run build && npm run preview &   # or point E2E_BASE_URL at a running instance
npm run test:e2e
```

## Deployment

### Free-tier Deployment (verified path this project actually uses)

See [DEPLOYMENT_FREE.md](DEPLOYMENT_FREE.md) - Neon (Postgres), Upstash (Redis), Aiven
(Kafka), Render (backend), Vercel (frontend).

### Hypothetical Cloud Architecture (not implemented)

Earlier versions of this README described AWS ECS + S3 + CloudFront + RDS + ElastiCache as
a "production" deployment target. None of that exists in this repository: there's no ECS
task definition, no Terraform/CDK, no S3 upload code (the AWS SDK dependency was removed -
image posts are a plain URL string), and no CloudFront config. If you want to build that out,
it's a reasonable next step, not something to claim as already done.

## Observability

- `/actuator/health`, `/actuator/metrics`, `/actuator/prometheus`
- Custom counters, all real and incremented by actual code paths (not aspirational):
  `posts.created`, `follows.created`, `likes.created`, `feed.cache.hit`, `feed.cache.miss`,
  `kafka.events.produced`, `kafka.notifications.consumed`, `websocket.notifications.sent`

## Known Limitations

- No Google Cloud test credentials in CI, so OAuth2 login is implemented but not
  runtime-verified end-to-end here - manual verification with real credentials is on you.
- Image "upload" is a URL field; there's no file upload endpoint or object storage.
- The feed is chronological only - no ranking, no ML personalization.
- No HTTPS enforcement inside the app itself.
- `react-router-dom` v6 has two known moderate CVEs with no non-breaking fix available at
  time of writing; upgrading to v7 is a reasonable follow-up but out of scope here.

See [docs/VERIFICATION_REPORT.md](docs/VERIFICATION_REPORT.md) for the complete list of what
was found, fixed, and how each item was actually verified.

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License.

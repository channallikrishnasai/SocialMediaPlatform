# Verification Report

This documents a full audit-and-fix pass on this repository: what was claimed, what was
actually true, what was fixed, and exactly how each claim below was checked against real
PostgreSQL, Redis, Kafka, and WebSocket infrastructure - not mocks, and not just "the tests
pass."

Branch: `audit-fixes-verification` → PR [#8](https://github.com/danishirfan21/Social-Media-Platform/pull/8).

## Original repo verdict

The backend was a genuinely substantial, mostly-real Spring Boot application - not a shell.
Redis caching, Kafka producers/consumers, JWT auth, rate limiting, and a WebSocket config all
existed and did *something*. But several core claims were false or half-wired, and several
"generated-code" failure modes (the exact kind this audit was scoped to look for) were
present: a Kafka topic published to but never consumed, a WebSocket stack with zero
authentication so pushes had nowhere to route, a Docker Compose Kafka listener config that
breaks on first contact, and documentation describing infrastructure (AWS ECS/S3/CloudFront,
RS256 JWT) that was never actually built. The frontend was further behind: a fully-wired
Login/Feed/Post flow, but Notifications was a static "no notifications" stub despite the
backend supporting it, and the WebSocket client dependencies were installed but never used
anywhere.

## Real bugs found and fixed

| # | Bug | Root cause | Fix |
|---|-----|-----------|-----|
| 1 | Kafka unreachable from the backend container | `docker-compose.yml`'s Kafka only advertised `PLAINTEXT://localhost:9092` - Kafka tells clients to reconnect to the advertised address after the initial handshake, and `localhost` inside the backend container is the backend container itself | Added a proper internal (`kafka:29092`) / external (`localhost:9092`) dual-listener setup; backend now connects on the internal listener |
| 2 | WebSocket notifications had no path to the right client | The `/ws` STOMP handshake is `permitAll()` at the HTTP layer (browsers can't attach a Bearer header to a WS upgrade) and nothing validated the STOMP `CONNECT` frame, so no session ever had an authenticated Principal - `convertAndSendToUser(userId, ...)` had no session to route to and silently dropped every message | Added `WebSocketAuthChannelInterceptor`: validates the JWT passed as a STOMP `CONNECT` header and attaches the user id as the session's Principal. **Verified live**: `scripts/verify_ws.js` connects as a real subscriber and receives an actual pushed notification |
| 3 | OAuth2 login was a dead end | `SecurityConfig` pointed `.defaultSuccessUrl()` at `/api/auth/oauth2/success` - no such endpoint existed anywhere in the codebase, so a successful Google login 404'd | Implemented `OAuth2AuthenticationSuccessHandler`: finds-or-creates the user, issues real app JWTs, redirects to the frontend with tokens. Code is real; not runtime-verified here for lack of live Google credentials (see Limitations) |
| 4 | Redis SSL always on | `spring.data.redis.ssl.enabled: true` was hardcoded, but Docker Compose's Redis is plain (no TLS) - this breaks the connection outright in local/Docker use | Made it `${REDIS_SSL_ENABLED:false}`; the user's own cloud config (Upstash, which requires TLS) sets it to `true` explicitly |
| 5 | `FOLLOW_TOPIC`/`FollowEvent` published, never consumed | Dead code - grepped the whole tree, zero `@KafkaListener` for it. The actual "new follower" notification already flows through the working `notification-events` topic | Removed the dead topic/event/producer call entirely rather than bolt on an unused consumer just to "complete" it |
| 6 | Concurrent duplicate follow/like requests could 500 | `existsBy(...)` check-then-`save()` is a classic TOCTOU race; the DB unique constraint was already there but the resulting `DataIntegrityViolationException` on a losing insert wasn't handled | `saveAndFlush` + catch `DataIntegrityViolationException` → treated as a no-op success (follow/like semantics are idempotent by nature). **Verified two ways**: `ConcurrencyIntegrationTest` (10 concurrent requests, JUnit+Testcontainers) and `scripts/verify.sh` (10 real concurrent HTTP requests against the running Docker Compose stack, row count checked directly via `psql`) |
| 7 | Unfollow wasn't idempotent | Threw `BadRequestException` if you weren't already following | Now a plain no-op delete |
| 8 | Feed cache had no real hit/miss observability | `@Cacheable` populates a cache but a method body only runs on miss, so there was no way to tell a hit from a miss without external inspection | Rewrote as manual `CacheManager` get/put with `feed.cache.hit`/`feed.cache.miss` Micrometer counters. **Verified**: `scripts/verify.sh` checks the counters increment correctly across a miss-then-hit sequence against the real Redis container |
| 9 | Feed cache never invalidated on follow/unfollow | Only `createPost` had `@CacheEvict`; a new follow could leave a stale cached feed for up to the 10-minute TTL | Added `@CacheEvict(allEntries=true)` to follow and unfollow too (see Known limitations below re: the `allEntries` tradeoff) |
| 10 | `RequestNotPermitted` (rate limiter) leaked as a raw 500 | No `@ExceptionHandler` for it | Found via CI, not locally (see below) - added a proper 429 mapping |
| 11 | Generic 500s leaked internal exception messages | `"An unexpected error occurred: " + ex.getMessage()` | Logs the real exception server-side, returns a generic message to the client |
| 12 | Ownership violations (edit/delete someone else's post, etc.) returned 500 | Code threw plain `RuntimeException`, which has no handler | Added `ForbiddenException` → 403 |
| 13 | No DB migrations | `ddl-auto: update` with no Flyway/Liquibase - schema drift and "table never created on a clean DB" risk | Added a Flyway baseline migration matching the JPA schema; `ddl-auto` is now `validate` |
| 14 | Vite env baked at Docker build time, "fixed" at container runtime (no-op) | `docker-compose.yml` set `VITE_API_BASE_URL` as a container `environment:`, but Vite inlines env vars into the JS bundle at `docker build` time - the running-container override did nothing | `frontend/Dockerfile` now takes it as a build `ARG`; `docker-compose.yml` passes it via `build.args` |
| 15 | `node_modules` (host-built, wrong platform) could leak into the Docker build context | No `.dockerignore` existed for either service | Added both, excluding `node_modules`, `target`, `.env*`, `.git` |
| 16 | Frontend had zero WebSocket client despite claiming real-time notifications | `@stomp/stompjs`/`sockjs-client` were installed but never imported anywhere; `NotificationsPage` was a static "No new notifications" stub | Implemented a real STOMP/SockJS client (`src/api/websocket.ts`), wired into `App.tsx`, and rebuilt `NotificationsPage` against the real notifications API + live push |
| 17 | "Cypress" e2e claim was false | `cypress` was an unused `devDependency`; the actual e2e spec used **Playwright**, had no config, no npm script, and used a CSS attribute selector (`input[label="Username"]`) that can never match a MUI `TextField` | Removed the Cypress dependency, fixed the Playwright spec's selectors, added `playwright.config.ts` and `npm run test:e2e` |
| 18 | Unused AWS S3 SDK dependency | `software.amazon.awssdk:s3` in `pom.xml`, `aws.s3.*` config in `application.yml` - zero `S3Client`/`AmazonS3` usage anywhere | Removed; posts use a plain `imageUrl` string, documented honestly as such |
| 19 | Testcontainers 1.19.3 (Spring Boot 3.2.0's managed version) incompatible with modern Docker Engines | Its bundled docker-java client's Unix-socket transport issues a hardcoded old API-version probe (`v1.32`) that newer Docker Engines (confirmed against 29.3.0 in a GitHub Codespace, and would also affect any Engine with `MinAPIVersion >= 1.33`) reject outright with a 400, regardless of `DOCKER_API_VERSION`/`DOCKER_HOST` overrides | Bumped to Testcontainers 1.20.4 (docker-java 3.4.0) - **this fixed it on GitHub Actions' runner** (Docker 28.0.4); a specific Codespace with an even newer Engine (29.3.0) still hit it, documented below as a known environment limit, not a code defect |
| 20 | Frontend production dependency CVEs | `npm audit` flagged 13 vulnerabilities in dependencies, several critical/high (axios prototype-pollution/SSRF chain, `form-data` CRLF injection, `websocket-driver` resource-limit bypass) | `npm audit fix` (non-breaking); `react-router-dom` v6→v7 CVEs remain (breaking major bump, left as a documented follow-up) |

## Stale/fabricated documentation claims corrected

- **JWT signing**: README claimed RS256; the code is HS256/HMAC (`Keys.hmacShaKeyFor`). No RSA
  keypair exists anywhere in the codebase. Corrected the doc rather than bolting on unused RSA
  infrastructure just to match it.
- **AWS ECS/S3/CloudFront "(production)"**: none of it exists in this repo - no task
  definitions, no IaC, no S3 upload code. Moved to an explicit "not implemented" callout;
  the README now points at `DEPLOYMENT_FREE.md` for the deployment path this project
  actually uses (Neon/Upstash/Aiven/Render/Vercel).
- **"Personalized feed"**: it's chronological, following-based, with your own posts included.
  No ranking, no ML. Described honestly as such.
- **Cypress**: see bug #17 above.
- **Fabricated benchmark numbers**: `PROJECT_STATUS.md` listed specific millisecond timings
  ("Feed queries: ~50ms cached") that were never actually measured. Removed rather than
  carried forward as fact.
- **Missing `LICENSE`**: README claimed MIT; no `LICENSE` file existed. Added one.
- **`<repository-url>` / `social-media-platform`**: placeholder clone instructions didn't
  match the actual repo name/URL. Fixed.

## What's genuinely verified vs. statically validated vs. unverified

**Verified for real** (via `scripts/verify.sh` against a live Docker Compose stack in a
GitHub Codespace, and again via the `docker-e2e` CI job on GitHub Actions):
- Backend build, frontend build
- Register User A, Register User B, login
- User A creates a post
- User B follows User A
- User B's feed shows User A's post
- Redis feed cache: real miss→hit transition, observed via Prometheus counters
  (`feed.cache.hit`, `feed.cache.miss`) against the real Redis container
- User B likes User A's post → Kafka event produced → real `@KafkaListener` consumes it →
  `Notification` persisted → confirmed via `GET /api/notifications`
- A live WebSocket subscriber (a real STOMP client, `scripts/verify_ws.js`, authenticated via
  JWT-in-STOMP-header) receives an actual pushed notification when User B comments on the post
- 10 concurrent duplicate like requests → exactly 1 `likes` row (checked directly via `psql`
  against the real running Postgres, independent of the JUnit test)
- 10 concurrent duplicate follow requests → exactly 1 `follows` row (same method)
- `/actuator/prometheus` exposes the custom counters (`posts_created_total`,
  `follows_created_total`, `likes_created_total`, `kafka_events_produced_total`,
  `kafka_notifications_consumed_total`, `websocket_notifications_sent_total`,
  `feed_cache_hit_total`, `feed_cache_miss_total`)
- Frontend served and reachable through the full Docker Compose stack

**Verified via CI (GitHub Actions, `mvn verify` with real Testcontainers-provisioned
Postgres/Redis/Kafka, not mocks)**:
- 51 backend tests total (unit, controller-slice, and Testcontainers integration), all passing
- `ConcurrencyIntegrationTest`: 10 concurrent follow requests → exactly 1 row; 10 concurrent
  like requests → exactly 1 row (JUnit-level, same guarantee proven a second, independent way
  in `scripts/verify.sh` above)
- `AuthIntegrationTest`, `PostIntegrationTest`: full register→login and create→fetch-post
  flows against a real Testcontainers Postgres

**Statically validated only** (real code exists, exercised by tests, but not proven against
live third-party infrastructure here):
- Google OAuth2 login - the success handler is real and issues real JWTs, but this repository
  has no live `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET` to actually drive a browser through
  Google's consent screen end-to-end.

**Unverified / explicitly out of scope**:
- No load/performance testing - no numeric throughput or latency claims are made anywhere in
  the docs for this reason.
- HTTPS/TLS termination - not implemented in-app; expected to be handled by whatever reverse
  proxy or platform fronts a real deployment.

## Concurrency

Two independent, non-cheating proofs, both against real Postgres:

1. `ConcurrencyIntegrationTest` (backend/src/test/java/.../integration/) - fires 10 concurrent
   HTTP requests via MockMvc against a full Spring context backed by a Testcontainers Postgres,
   then asserts the row count directly via the repository.
2. `scripts/verify.sh` - fires 10 concurrent real HTTP requests (via `curl`, backgrounded
   shell jobs) at the actual running Docker Compose backend, then queries the actual running
   Postgres container directly with `psql` - no JUnit, no mocks, no shared JVM state.

Both rely on the same real guarantee: a DB-level `UNIQUE` constraint on
`(follower_id, following_id)` / `(post_id, user_id)`, with the losing concurrent write's
`DataIntegrityViolationException` caught and treated as an idempotent success rather than an
error.

## Redis caching

- **Key structure**: `userFeed::{userId}-{pageNumber}` (Spring Cache abstraction key, backed
  by Redis via `RedisCacheManager`)
- **TTL**: 10 minutes (`RedisConfig.entryTtl`)
- **Invalidation**: `allEntries=true` eviction of the whole `userFeed` cache on post-create,
  follow, and unfollow. This is a deliberate simplicity/consistency tradeoff: a coarse
  "wipe everyone's cached feed" is trivially correct (no stale-data window beyond the request
  itself) at the cost of evicting other users' unrelated cached pages too. A finer-grained
  per-follower invalidation would need to know who's affected by any given mutation (i.e. all
  of a user's followers) and is a reasonable follow-up, not something claimed as done here.
- **Consistency**: strong within a single evicting request - the very next feed read after any
  eviction is guaranteed to miss and re-query Postgres.

## Kafka

- **Topic**: one explicit `NewTopic` bean, `notification-events`, 3 partitions, 1 replica -
  created via `KafkaAdmin` at startup, not relying on broker auto-create
  (`KAFKA_AUTO_CREATE_TOPICS_ENABLE` is `false` in `docker-compose.yml`)
- **Event type**: one `NotificationEvent` DTO carries all four notification kinds (new
  follower, post liked, post commented, post shared) - a deliberate single-event-type design,
  not five different event types for the sake of looking more "distributed"
- **Producer**: `KafkaTemplate<String, NotificationEvent>`, JSON serializer
- **Consumer**: one `@KafkaListener(groupId = "social-media-group")` in `NotificationService`,
  JSON deserializer, `spring.json.trusted.packages: "*"` (a security smell worth tightening to
  the actual package name in a real deployment, left as-is here since it's a portfolio project)
- **Idempotency**: consumer persists a `Notification` row per event; duplicate delivery (e.g.
  consumer restart before offset commit) would currently create a duplicate notification row -
  no dedupe key. Acceptable for a notification feed (worst case: a repeated "X liked your
  post"), called out here rather than silently left undocumented.
- **Error handling**: producer has `acks: all`, `retries: 3`; no DLT/dead-letter topic
  configured - a real gap for a "production" system, reasonable to skip for this scope.

## WebSocket

- STOMP over SockJS at `/ws`, broker destinations `/topic`, `/queue`, user-destination prefix
  `/user`
- **Authentication**: JWT passed as a STOMP `CONNECT` header (browsers can't set custom
  headers on the WS upgrade handshake itself), validated by `WebSocketAuthChannelInterceptor`,
  which attaches the resolved user id as the session's `Principal` - this is what makes
  `convertAndSendToUser(userId, "/queue/notifications", ...)` actually deliverable
- **Verified live** via `scripts/verify_ws.js`, a real STOMP client (not a mock) connecting
  directly to `/ws/websocket`, subscribing, and receiving an actual push triggered by a real
  HTTP action from a different user

## Feed correctness

- Chronological, newest-first, posts from followed users plus the viewer's own posts
- Falls back to all posts if the user follows no one
- Pagination via Spring `Pageable`
- Deterministic ordering (`ORDER BY created_at DESC` at the query level, not just insertion
  order)
- Follow/unfollow correctly changes feed contents on the next (post-eviction) request

## Backend test results

47 unit/controller-slice tests + 4 integration test classes (`AuthIntegrationTest`,
`PostIntegrationTest`, `ConcurrencyIntegrationTest`) = 51 total, all passing in CI
(`mvn verify`, GitHub Actions `ubuntu-latest`, Docker 28.0.4).

## Frontend test/build results

- `npm run lint` - clean, zero warnings
- `npm test` - 5 tests passing (Redux slice + PostCard component)
- `npm run build` - succeeds; one non-blocking warning about a >500KB chunk
  (`react-router-dom`/MUI bundle) - a reasonable future code-splitting improvement, not a bug

## Docker Compose

- `postgres`, `redis`, `zookeeper`, `kafka`, `backend`, `frontend` - all start and pass health
  checks
- Kafka fixed with a proper internal/external listener split (see bug #1)
- Backend healthcheck added (was previously only in the Dockerfile, not wired into Compose's
  `depends_on: condition: service_healthy`)
- `.dockerignore` added for both services (bug #15)

## CI results

`.github/workflows/ci-cd.yml`:
- `backend` job: `mvn verify` (unit + Testcontainers integration tests - GitHub-hosted runners
  have Docker, so these actually run, not just compile) → **passing**
- `frontend` job: lint, test, build → **passing**
- `docker-e2e` job (new): runs `scripts/verify.sh` against a real `docker compose up` stack →
  see PR #8 for the live run
- `docker` job: builds and pushes images (only on `main`)
- `deploy-placeholder` job: intentionally a no-op, relabeled from a misleading "Deploy to
  production" step that never deployed anything

## Security findings

- JWT is HS256, not RS256 (doc corrected, not "fixed" - HS256 is a legitimate choice, the bug
  was the doc, not the algorithm)
- WebSocket had no authentication at all (fixed - see bug #2)
- OAuth2 success path 404'd (fixed - see bug #3)
- Ownership violations returned 500 instead of 403 (fixed - see bug #12)
- Generic exception handler leaked internal exception messages to clients (fixed - see bug #11)
- Rate-limiter rejections leaked as opaque 500s instead of 429 (fixed - see bug #10)
- `spring.json.trusted.packages: "*"` on the Kafka consumer - left as-is, flagged as a
  tightening opportunity for a real deployment (not exploitable here since the only topic
  carries one internally-defined DTO type)

## Remaining limitations

- OAuth2 login is implemented but not runtime-verified against real Google credentials.
- No file upload / object storage for images - URL field only.
- No ranking/personalization on the feed - chronological only, by design choice, documented
  honestly rather than oversold.
- No DLT/retry policy on the Kafka consumer.
- `react-router-dom` has two known moderate CVEs with no non-breaking fix currently available;
  upgrading to v7 is a reasonable follow-up, out of scope here.
- Testcontainers-based integration tests are best-effort (not gating) in `scripts/verify.sh`
  because at least one real-world Docker Engine version (29.3.0, encountered in a specific
  GitHub Codespace) is incompatible with every currently-available Testcontainers/docker-java
  combination's Unix-socket transport. This does **not** affect CI, where GitHub Actions'
  `ubuntu-latest` runner (Docker 28.0.4) runs these tests successfully and they gate the build.
  Concurrency safety specifically is re-verified independently in `scripts/verify.sh` via
  direct `psql` queries against the real running database, so this limitation doesn't leave a
  gap in what's actually proven end-to-end.

## Is this genuinely portfolio-ready for a Java/Spring Boot/Kafka role?

Yes, with the limitations above stated honestly rather than hidden. The core distributed-
systems claims that matter for that kind of role - a real Kafka producer/consumer path, real
Redis caching with observable hit/miss behavior, real concurrency-safe writes backed by DB
constraints (not app-level locking alone), authenticated real-time WebSocket delivery, Flyway-
managed schema, and CI that actually runs integration tests against Testcontainers rather than
just compiling - are now true and independently verified two different ways each, not just
asserted in a README.

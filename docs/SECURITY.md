# Security

This project implements JWT-based API authentication and resource authorization. The current configuration also contains development-oriented settings that must be reviewed before deployment.

## Trust boundaries

- Frontend clients authenticate with access + refresh JWTs.
- Protected endpoints are enforced server-side in Spring Security.
- Resource ownership checks prevent a user from modifying or deleting another user’s content or notifications.
- Kafka events and Redis state are treated as non-authoritative input to business logic—they are still validated by the backend service layer.

## Authentication and authorization

- Passwords are stored with BCrypt/PasswordEncoder-backed hashing through Spring Security.
- JWTs are validated on each request by the authentication filter.
- Refresh tokens are distinct from access tokens and should not be accepted in access-token contexts.
- Ownership checks are enforced for posts, notifications, and other user-scoped resources.
- Protected routes require authentication and user-specific authorization checks.

## Configuration hardening

The project keeps development convenience separate from production settings:

- Local defaults are intentionally placeholder values in `.env.example` files.
- Actual secrets must be supplied through real environment variables or a secure secret manager.
- The backend does not ship live credentials in source control.
- Docker Compose credentials are examples only and should never be used in production.

## CORS and web security

- The current `SecurityConfig` allows all origin patterns (`*`) while allowing credentials. This is permissive and must be restricted to trusted origins before deployment.
- CSRF protection is disabled in the current stateless API configuration. Reassess this if browser-cookie authentication or other stateful browser flows are introduced.
- Actuator exposure is limited to health, info, metrics, and Prometheus endpoints, but `/actuator/**` is currently permitted by the HTTP security rules.
- Swagger/OpenAPI remains publicly permitted by the current configuration and should be restricted in production deployments.

## WebSocket and OAuth

- WebSocket authentication requires a valid JWT in the STOMP `CONNECT` frame; the HTTP `/ws` handshake itself is permitted.
- The Google OAuth client and backend success handler are configured, but the frontend has no sign-in entry or token-capture route. The handler redirects JWTs in URL query parameters; do not expose this as a usable production flow without redesigning token delivery. External-provider login also requires valid client credentials.
- Redirect URIs must match the actual deployed frontend and Google OAuth configuration exactly.

## Production precautions

Before deployment, verify the following:

1. Replace all placeholder JWT secrets, database credentials, and OAuth client values.
2. Restrict CORS origins to the real deployment domain.
3. Use TLS and secure cookies or browser storage in production.
4. Keep Redis, PostgreSQL, and Kafka behind private networks or properly controlled access rules.
5. Review Kafka listeners and advertised addresses to avoid exposing internal service names incorrectly.
6. Keep the application behind an API gateway or reverse proxy with proper rate limiting and HTTP security headers.
7. Rotate any secret that was ever checked into a git history or leaked into a shared environment.

## Known limitations

- Docker-based verification requires a real Docker daemon and is therefore environment-dependent.
- Live OAuth verification depends on proper Google client configuration, not sample values.
- This document does not claim a formal security certification; it documents the implemented security model and deployment safeguards.

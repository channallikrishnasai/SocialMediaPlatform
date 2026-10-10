# Architecture

This repository contains a Java 17/Spring Boot backend and a React + TypeScript frontend for a social media platform with follow/feed logic, post interactions, notifications, and real-time WebSocket delivery.

## Runtime components

- Backend: Spring Boot 3.2, Spring Security, JPA, Kafka, Redis, Flyway, Actuator
- Frontend: React 18, Vite, Redux Toolkit, TanStack Query, MUI
- Data stores: PostgreSQL + Redis
- Eventing: Kafka topic `notification-events`
- Real-time delivery: STOMP/WebSocket via authenticated JWT connection
- Container orchestration: Docker Compose

## System diagram

```mermaid
flowchart LR
    User[Browser / Mobile client] --> FE[React frontend]
    FE --> API[Spring Boot REST API]
    API --> PG[(PostgreSQL)]
    API --> REDIS[(Redis)]
    API --> KAFKA[(Kafka: notification-events)]
    KAFKA --> CONSUMER[Notification consumer]
    CONSUMER --> PG
    CONSUMER --> WS[WebSocket/Stomp clients]
    FE --> WS
    API --> JWT[JWT auth + refresh]
```

## Request flows

### Authentication flow

```mermaid
sequenceDiagram
    participant Client
    participant Frontend
    participant API
    participant Security
    participant DB

    Client->>Frontend: Register / login
    Frontend->>API: POST /api/auth/register or /api/auth/login
    API->>Security: Validate credentials and issue JWT
    Security->>DB: Load user + roles
    DB-->>API: User entity
    API-->>Frontend: Access + refresh tokens + user payload
    Frontend->>API: Authorization header on protected requests
```

### Post + feed flow

```mermaid
sequenceDiagram
    participant Client
    participant Frontend
    participant API
    participant DB
    participant Cache

    Client->>Frontend: Create post
    Frontend->>API: POST /api/posts
    API->>DB: Insert post
    API->>Cache: Evict user feed cache
    API-->>Frontend: Created post response
    Client->>API: GET /api/feed
    API->>Cache: Check cache
    alt cache miss
        Cache-->>API: miss
        API->>DB: Query followed users + posts
        API->>Cache: Store page
    else cache hit
        Cache-->>API: hit
    end
    API-->>Frontend: Paginated feed
```

### Notification delivery flow

```mermaid
sequenceDiagram
    participant Actor as User action
    participant API
    participant Kafka
    participant Consumer
    participant DB
    participant WS
    participant Client

    Actor->>API: Like / follow / comment / share
    API->>Kafka: Emit NotificationEvent
    Kafka->>Consumer: Deliver event
    Consumer->>DB: Persist notification
    Consumer->>WS: Push live update
    WS-->>Client: WebSocket notification
```

## Key backend packages

- `backend/src/main/java/com/socialmedia/platform/controller` — HTTP controllers
- `backend/src/main/java/com/socialmedia/platform/service` — business logic
- `backend/src/main/java/com/socialmedia/platform/repository` — Spring Data repositories
- `backend/src/main/java/com/socialmedia/platform/security` — JWT, SecurityConfig, UserPrincipal
- `backend/src/main/java/com/socialmedia/platform/entity` — persistence model
- `backend/src/main/java/com/socialmedia/platform/event` — Kafka event payloads
- `backend/src/main/resources/db/migration` — Flyway migrations

## Key frontend packages

- `frontend/src/pages` — page-level screens
- `frontend/src/components` — reusable UI pieces
- `frontend/src/api` — Axios and WebSocket clients
- `frontend/src/redux` — auth + notification state
- `frontend/src/utils` — shared helpers and theme definitions

## Deployment topology

The repository ships with Docker Compose for local development and verification. The runtime stack is:

- PostgreSQL on port 5432
- Redis on port 6379
- Kafka on port 9092 (with ZooKeeper)
- Spring Boot backend on port 8080
- Frontend on port 3000

## Known limitations

- Google OAuth works only with real credentials in a provider-configured environment.
- Integration tests require a working Docker daemon.
- No production deployment target is wired to a managed cloud environment by default.
- The project intentionally avoids claiming cloud deployment when none is configured.

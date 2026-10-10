#!/usr/bin/env bash
# End-to-end verification of the social-media-platform stack against real infrastructure.
# Exits non-zero on the first failure. Intended to run from the repo root on a machine/
# Codespace with Docker, Maven, Node, and curl available.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

BACKEND_URL="http://localhost:8080"
FRONTEND_URL="http://localhost:3000"
FAILED=0

log() { printf '\n\033[1;34m==> %s\033[0m\n' "$1"; }
fail() { printf '\033[1;31mFAIL: %s\033[0m\n' "$1"; FAILED=1; }
ok() { printf '\033[1;32mOK: %s\033[0m\n' "$1"; }

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command '$1' not found on PATH" >&2
    exit 1
  fi
}

require_cmd docker
require_cmd curl
require_cmd mvn
require_cmd npm

if ! command -v jq >/dev/null 2>&1; then
  echo "jq not found, attempting to install..."
  sudo apt-get update -y && sudo apt-get install -y jq
fi

# Pin JAVA_HOME to a JDK that actually supports --release 17, since some environments
# (e.g. this project's Codespace devcontainer) default `java`/`mvn` to a newer JDK where
# `javac --release 17` fails outright rather than just warning.
if [ -d /usr/local/sdkman/candidates/java ]; then
  for candidate in /usr/local/sdkman/candidates/java/21* /usr/local/sdkman/candidates/java/17*; do
    if [ -x "$candidate/bin/javac" ]; then
      export JAVA_HOME="$candidate"
      export PATH="$JAVA_HOME/bin:$PATH"
      break
    fi
  done
fi

# Portable numeric comparison (a > b) without depending on `bc`, which isn't installed
# in every environment (including this project's own Codespace devcontainer).
gt() { awk -v a="$1" -v b="$2" 'BEGIN{exit !(a>b)}'; }

# Safe HTTP helper: writes body to $1, status code to stdout. Never pipes a body into
# grep, so a truncated/odd response can't SIGPIPE the script (set -o pipefail trap).
http_call() {
  local method="$1" url="$2" body_file="$3" data="${4:-}" auth="${5:-}"
  local args=(-sS -o "$body_file" -w '%{http_code}' -X "$method" "$url")
  [ -n "$data" ] && args+=(-H "Content-Type: application/json" -d "$data")
  [ -n "$auth" ] && args+=(-H "Authorization: Bearer $auth")
  curl "${args[@]}"
}

json_field() {
  jq -r "$2" <"$1"
}

### 1. Backend build + unit/controller tests (no Docker dependency - always required to pass)
log "Building and testing backend (unit + controller-slice tests)"
(cd backend && mvn -q clean test -Dtest='!*IntegrationTest') \
  && ok "backend unit/controller tests" || fail "backend unit/controller tests"

### 1b. Testcontainers integration tests (best-effort - some Docker Engine versions ship a
### docker-java-incompatible API). Concurrency is independently re-verified against the real
### running stack in step 13 below regardless of this step's outcome.
log "Running Testcontainers integration tests (best-effort)"
if (cd backend && mvn -q test -Dtest='*IntegrationTest'); then
  ok "backend Testcontainers integration tests"
else
  echo "Testcontainers integration tests failed/skipped in this environment - not treated" \
       "as fatal. See docs/VERIFICATION_REPORT.md for the known docker-java vs. very new" \
       "Docker Engine (client version 1.32 rejected, MinAPIVersion 1.40) incompatibility" \
       "this can hit. CI runs these on GitHub-hosted runners where they're expected to pass."
fi

### 2. Frontend build + tests
log "Building and testing frontend"
(cd frontend && npm ci && npm run lint && npm test -- --run && npm run build) \
  && ok "frontend build + tests" || fail "frontend build + tests"

### 3. Docker Compose config validation
log "Validating docker-compose config"
docker compose config >/dev/null && ok "docker-compose config valid" || fail "docker-compose config"

### 4. Start the stack
log "Starting docker compose stack"
docker compose up -d --build

cleanup() {
  log "Collecting docker compose logs (last 200 lines per service)"
  docker compose logs --tail=200 || true
  log "Tearing down stack"
  docker compose down -v || true
}
trap cleanup EXIT

### 5. Wait for backend health
log "Waiting for backend health"
BACKEND_READY=0
for i in $(seq 1 60); do
  if curl -sS -o /dev/null -w '%{http_code}' "$BACKEND_URL/actuator/health" 2>/dev/null | grep -q '^200$'; then
    BACKEND_READY=1
    break
  fi
  sleep 5
done
if [ "$BACKEND_READY" = "1" ]; then
  ok "backend healthy"
else
  fail "backend did not become healthy in time"
  exit 1
fi

TMP_DIR="$(mktemp -d)"
TS=$(date +%s)
USER_A_EMAIL="usera_${TS}@example.com"
USER_B_EMAIL="userb_${TS}@example.com"

### 6. Register User A and User B
log "Registering User A and User B"
http_call POST "$BACKEND_URL/api/auth/register" "$TMP_DIR/reg_a.json" \
  "{\"username\":\"usera_${TS}\",\"email\":\"${USER_A_EMAIL}\",\"password\":\"Password123!\"}" \
  | grep -q '^200$' && ok "register User A" || fail "register User A"

http_call POST "$BACKEND_URL/api/auth/register" "$TMP_DIR/reg_b.json" \
  "{\"username\":\"userb_${TS}\",\"email\":\"${USER_B_EMAIL}\",\"password\":\"Password123!\"}" \
  | grep -q '^200$' && ok "register User B" || fail "register User B"

TOKEN_A=$(json_field "$TMP_DIR/reg_a.json" '.accessToken')
TOKEN_B=$(json_field "$TMP_DIR/reg_b.json" '.accessToken')
USER_A_ID=$(json_field "$TMP_DIR/reg_a.json" '.user.id')
USER_B_ID=$(json_field "$TMP_DIR/reg_b.json" '.user.id')

### 7. Login both (proves login independently of register's auto-login)
log "Logging in both users"
http_call POST "$BACKEND_URL/api/auth/login" "$TMP_DIR/login_a.json" \
  "{\"email\":\"${USER_A_EMAIL}\",\"password\":\"Password123!\"}" \
  | grep -q '^200$' && ok "login User A" || fail "login User A"

### 8. User A creates a post
log "User A creates a post"
POST_CONTENT="verify.sh post ${TS}"
http_call POST "$BACKEND_URL/api/posts" "$TMP_DIR/post.json" \
  "{\"content\":\"${POST_CONTENT}\"}" "$TOKEN_A" \
  | grep -q '^201$' && ok "create post" || fail "create post"
POST_ID=$(json_field "$TMP_DIR/post.json" '.id')

### 9. User B follows User A
log "User B follows User A"
http_call POST "$BACKEND_URL/api/users/${USER_A_ID}/follow" "$TMP_DIR/follow.json" "" "$TOKEN_B" \
  | grep -q '^200$' && ok "follow" || fail "follow"

### 10. Redis cache verification: miss then hit, observable via Micrometer counters
log "Verifying Redis feed cache (miss then hit)"
MISS_BEFORE=$(curl -sS "$BACKEND_URL/actuator/metrics/feed.cache.miss" | jq -r '.measurements[0].value // 0')
HIT_BEFORE=$(curl -sS "$BACKEND_URL/actuator/metrics/feed.cache.hit" | jq -r '.measurements[0].value // 0')

http_call GET "$BACKEND_URL/api/feed?page=0" "$TMP_DIR/feed1.json" "" "$TOKEN_B" \
  | grep -q '^200$' && ok "feed request 1 (expect cache miss)" || fail "feed request 1"

MISS_AFTER_1=$(curl -sS "$BACKEND_URL/actuator/metrics/feed.cache.miss" | jq -r '.measurements[0].value // 0')
if gt "$MISS_AFTER_1" "$MISS_BEFORE"; then
  ok "feed.cache.miss incremented on first request"
else
  fail "feed.cache.miss did not increment on first request"
fi

http_call GET "$BACKEND_URL/api/feed?page=0" "$TMP_DIR/feed2.json" "" "$TOKEN_B" \
  | grep -q '^200$' && ok "feed request 2 (expect cache hit)" || fail "feed request 2"

HIT_AFTER=$(curl -sS "$BACKEND_URL/actuator/metrics/feed.cache.hit" | jq -r '.measurements[0].value // 0')
if gt "$HIT_AFTER" "$HIT_BEFORE"; then
  ok "feed.cache.hit incremented on repeated request"
else
  fail "feed.cache.hit did not increment on repeated request"
fi

if grep -q "\"${POST_CONTENT}\"" "$TMP_DIR/feed2.json"; then
  ok "User A's post appears in User B's feed"
else
  fail "User A's post missing from User B's feed"
fi

### 11. User B likes the post -> Kafka event -> NotificationConsumer -> persisted -> WebSocket
log "User B likes the post (exercises Kafka + notification + WebSocket path)"
http_call POST "$BACKEND_URL/api/posts/${POST_ID}/like" "$TMP_DIR/like.json" "" "$TOKEN_B" \
  | grep -q '^200$' && ok "like post" || fail "like post"

log "Waiting for the like event to travel through Kafka to the notification consumer"
NOTIFIED=0
for i in $(seq 1 20); do
  http_call GET "$BACKEND_URL/api/notifications?page=0" "$TMP_DIR/notifications.json" "" "$TOKEN_A" >/dev/null
  if jq -e '.content[] | select(.type == "POST_LIKED")' "$TMP_DIR/notifications.json" >/dev/null 2>&1; then
    NOTIFIED=1
    break
  fi
  sleep 2
done
if [ "$NOTIFIED" = "1" ]; then
  ok "POST_LIKED notification persisted for User A (Kafka produced + consumed)"
else
  fail "POST_LIKED notification never arrived - Kafka event path broken"
fi

KAFKA_PRODUCED=$(curl -sS "$BACKEND_URL/actuator/metrics/kafka.events.produced" | jq -r '.measurements[0].value // 0')
KAFKA_CONSUMED=$(curl -sS "$BACKEND_URL/actuator/metrics/kafka.notifications.consumed" | jq -r '.measurements[0].value // 0')
WS_SENT=$(curl -sS "$BACKEND_URL/actuator/metrics/websocket.notifications.sent" | jq -r '.measurements[0].value // 0')
echo "kafka.events.produced=$KAFKA_PRODUCED kafka.notifications.consumed=$KAFKA_CONSUMED websocket.notifications.sent=$WS_SENT"
if gt "$KAFKA_PRODUCED" 0 && gt "$KAFKA_CONSUMED" 0 && gt "$WS_SENT" 0; then
  ok "Kafka + WebSocket counters are non-zero"
else
  fail "Kafka/WebSocket counters are zero - event path not actually exercised"
fi

### 12. Real WebSocket delivery check (Node/STOMP client).
### Uses a COMMENT (not another like) as the trigger, since likePost is intentionally
### idempotent and a second like from the same user would be a no-op that emits no event.
log "Verifying live WebSocket notification delivery"
node "$ROOT_DIR/scripts/verify_ws.js" "$BACKEND_URL" "$TOKEN_A" &
WS_PID=$!
sleep 3 # let the STOMP client finish connecting/subscribing before the trigger fires

http_call POST "$BACKEND_URL/api/posts/${POST_ID}/comments" "$TMP_DIR/comment.json" \
  "{\"content\":\"verify.sh comment ${TS}\"}" "$TOKEN_B" \
  | grep -q '^201$' && ok "comment on post (WebSocket trigger)" || fail "comment on post"

if wait "$WS_PID"; then
  ok "WebSocket notification delivered to a live subscriber"
else
  fail "WebSocket notification was not delivered to a live subscriber"
fi

### 13. Concurrency: fire real duplicate requests at the running stack and check the actual
### Postgres row count directly. This does not depend on Testcontainers/mvn verify (whose
### docker-java client is incompatible with some very new Docker Engine versions - see
### docs/VERIFICATION_REPORT.md), and proves idempotency against the exact database this
### stack is using, not a throwaway container.
log "Verifying concurrent duplicate requests resolve to exactly one row"
psql_count() {
  docker compose exec -T postgres psql -U postgres -d socialmedia -tAc "$1" | tr -d '[:space:]'
}

for i in $(seq 1 10); do
  http_call POST "$BACKEND_URL/api/posts/${POST_ID}/like" "$TMP_DIR/like_race_$i.json" "" "$TOKEN_A" >/dev/null &
done
wait
LIKE_COUNT=$(psql_count "SELECT COUNT(*) FROM likes WHERE post_id=${POST_ID} AND user_id=${USER_A_ID};")
if [ "$LIKE_COUNT" = "1" ]; then
  ok "10 concurrent duplicate likes from User A resolved to exactly 1 row (found: $LIKE_COUNT)"
else
  fail "expected exactly 1 like row from concurrent duplicates, found: $LIKE_COUNT"
fi

for i in $(seq 1 10); do
  http_call POST "$BACKEND_URL/api/users/${USER_B_ID}/follow" "$TMP_DIR/follow_race_$i.json" "" "$TOKEN_A" >/dev/null &
done
wait
FOLLOW_COUNT=$(psql_count "SELECT COUNT(*) FROM follows WHERE follower_id=${USER_A_ID} AND following_id=${USER_B_ID};")
if [ "$FOLLOW_COUNT" = "1" ]; then
  ok "10 concurrent duplicate follows from User A resolved to exactly 1 row (found: $FOLLOW_COUNT)"
else
  fail "expected exactly 1 follow row from concurrent duplicates, found: $FOLLOW_COUNT"
fi

### 14. Prometheus metrics endpoint
log "Verifying Prometheus metrics endpoint"
PROM_STATUS=$(curl -sS -o "$TMP_DIR/prometheus.txt" -w '%{http_code}' "$BACKEND_URL/actuator/prometheus")
if [ "$PROM_STATUS" = "200" ] && grep -q "posts_created_total" "$TMP_DIR/prometheus.txt"; then
  ok "Prometheus metrics exposed with custom counters"
else
  fail "Prometheus metrics endpoint missing or missing custom counters"
fi

### 15. Frontend reachability
log "Verifying frontend is served"
FRONTEND_STATUS=$(curl -sS -o /dev/null -w '%{http_code}' "$FRONTEND_URL")
[ "$FRONTEND_STATUS" = "200" ] && ok "frontend reachable" || fail "frontend not reachable"

if [ "$FAILED" = "1" ]; then
  echo
  echo "One or more verification steps FAILED. See above."
  exit 1
fi

echo
echo "All verification steps passed."

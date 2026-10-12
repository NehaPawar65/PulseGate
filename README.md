# PulseGate: API Traffic Gateway

[![CI](https://github.com/NehaPawar65/PulseGate/actions/workflows/ci.yml/badge.svg)](https://github.com/NehaPawar65/PulseGate/actions/workflows/ci.yml)

PulseGate sits in front of an API, identifies each caller by API key, and enforces a per-client rate limit. Requests within quota reach the protected endpoint. Excess requests get **HTTP 429** with a `Retry-After` header. Every decision is recorded, and a React dashboard shows live traffic, latency and per-client rejection rates. A built-in simulator sends real concurrent HTTP traffic so you can compare **Token Bucket** and **Fixed Window** on the same workload. Quotas can be held in memory (single instance) or in **Redis**, which lets several gateway instances share one limit.

**Stack:** Java 21 · Spring Boot 4 · PostgreSQL 16 · Redis 7 · React 19 · Vite · Tailwind CSS · Recharts · JUnit 5 · Mockito · Swagger/OpenAPI · GitHub Actions

![Simulator: Token Bucket run](docs/screenshots/simulator-token-bucket.png)

---

## What it does

| | |
|---|---|
| **Per-client policies** | Each client is assigned a policy: Token Bucket (burst capacity + refill rate) or Fixed Window (N requests per window). Editing a policy takes effect on the client's next request. |
| **HTTP 429 with useful headers** | `X-RateLimit-Limit`, `X-RateLimit-Remaining`, and `Retry-After` on rejection. Errors use RFC 9457 `ProblemDetail` JSON. |
| **Shared quotas across instances** | With `PULSEGATE_LIMITER_STORE=redis`, each check is one atomic Lua script, so N gateway instances enforce one limit per client. |
| **API keys stored as hashes** | Keys are 192-bit random values, shown once at registration. Only the SHA-256 hash is stored, and raw keys are never logged. |
| **Admin authentication** | Every admin API, and the dashboard, requires a bearer token (`PULSEGATE_ADMIN_TOKEN`). |
| **Protection from key guessing** | Each IP gets 30 failed key attempts (refilling 1/s). After that, its requests get 429 **before any database work**. |
| **Request history** | Every decision (allowed / rate limited / invalid key) is recorded with latency, written in the background in batches. |
| **Traffic simulator** | N concurrent workers send real HTTP requests through the gateway and report allowed/rejected counts, p50/p95/p99 latency, and outcome over time. |
| **Dashboard** | Totals, rejection rate, per-minute timeline, server-side latency percentiles, per-client breakdown, filterable request log. |

## Architecture

```mermaid
flowchart LR
    UI["React dashboard<br/>(Vite, :5173)"] -- "REST + admin token" --> API
    Caller["API client<br/>X-API-Key"] --> GW
    subgraph Spring Boot :8080
      API["Admin APIs (bearer token)<br/>policies · clients · analytics · events · simulations"]
      GW["RateLimitInterceptor<br/>/api/protected/**"] --> IKG["InvalidKeyGuard<br/>per-IP failed attempts"]
      GW --> CL["ClientLookupService<br/>key-hash cache (Caffeine)"]
      GW --> RL["RateLimiterService<br/>in-memory or Redis"]
      GW -- allowed --> P["ProtectedController<br/>(sample upstream API)"]
      GW -- enqueue --> REC["RequestEventRecorder<br/>bounded queue + batch writer"]
      SIM["SimulationService<br/>closed-loop load generator"] -- real HTTP --> GW
    end
    CL -- cache miss --> DB[(PostgreSQL<br/>clients · rate_limit_policies · request_events)]
    REC -- batched INSERT --> DB
    API --> DB
    RL -. "store=redis" .-> R[(Redis<br/>shared quotas)]
```

### Request flow (`GET /api/protected/resource`)

1. `RateLimitInterceptor.preHandle`: if this IP has used up its failed-key allowance, return **429** at once, with no database work.
2. Hash the `X-API-Key` and resolve the client and policy through `ClientLookupService`. This is served from memory after the first request (30 s TTL), so known clients **skip the database**.
3. Missing, unknown or inactive key → **401**, recorded as `REJECTED_INVALID_KEY`, and one failed attempt is charged to the IP.
4. `RateLimiterService.tryConsume()`: either the in-memory limiter, or one atomic Redis Lua script.
5. Out of quota → **429** + `Retry-After`, recorded as `REJECTED_RATE_LIMIT`. The controller never runs.
6. Allowed → the controller runs, the response carries the `X-RateLimit-*` headers, and `afterCompletion` records `ALLOWED` with end-to-end server latency.

Recording only **enqueues**. A background thread writes events in batches, so no request waits for the database.

### Key design decisions

**Token Bucket: O(1) time and memory per request.** Each bucket holds two fields, `tokens` (a double) and `lastRefillNanos`. There is no timer thread: each call adds `elapsed × rate` tokens (capped at capacity), then takes one if `tokens ≥ 1`. It uses `System.nanoTime()` (monotonic) rather than wall-clock time, so NTP adjustments can't create or destroy tokens.

**Concurrency: no token is ever spent twice.**
- `tryConsume()` is `synchronized`, so refill-then-take is one atomic step per bucket. A test fires 5,000 attempts from 50 threads at a 1,000-token bucket and asserts exactly 1,000 succeed.
- Limiters live in a `ConcurrentHashMap` and are created with `compute()`. Two simultaneous *first* requests for a client therefore can't each create a full bucket.
- In Redis mode, the read-refill-take sequence is **one Lua script**. Redis runs scripts one at a time and uses its own clock (`TIME`), so instances can't interleave and clock skew between servers doesn't matter.

**Failure behaviour: limits are never switched off.**
- If the event database fails, the error is logged and the response is unchanged. The limit decision was already made.
- If Redis is unreachable, the gateway falls back to per-instance in-memory limits: weaker, but still enforced. The connection is warmed up at startup, so the first requests don't time out into the fallback.
- If the event queue is full, events are **dropped and counted** rather than blocking request threads.

**Policy changes without restarts.** The limiter stores the `LimitSpec` it was built from (in memory) or includes it in the key (in Redis). A changed policy gets a fresh limiter on the next request. Policy edits also clear the client cache; other instances pick up the change within the cache TTL.

**Why SHA-256 and not bcrypt for API keys?** bcrypt is designed for low-entropy passwords. A 192-bit random key can't be brute-forced, so a fast deterministic hash is safe, and it allows an indexed `WHERE api_key_hash = ?` lookup. bcrypt's per-row salt would force comparing against every row.

**Admin token check.** The token is compared with `MessageDigest.isEqual` (constant time), so response timing doesn't reveal how much of a guess was right.

## Measured results

All numbers below come from real runs on one machine. They are relative comparisons, not production benchmarks.

**Environment:** Windows 11 · Intel Core i5-12450H (12 logical cores) · Java 21.0.11 · PostgreSQL 16 and Redis 7.4 in Docker Desktop · load generator running on the same machine as the gateway.

### Caching + asynchronous event writes: before vs after

Same workload both times: 3 runs × 3,000 requests at concurrency 20, with every request allowed (the full path), and 9,000 events recorded each time.

| Server-side gateway latency | Before | After |
|---|---|---|
| p50 | 25.2 ms | **0.22 ms** |
| p95 | 134.4 ms | **7.0 ms** |
| p99 | 272.7 ms | **42.4 ms** |
| Events recorded | 9,000 / 9,000 | 9,000 / 9,000 (0 dropped) |

Client-side throughput varies a lot between runs, because the load generator shares the CPU (before: 270 / 146 / 653 req/s, after: 584 / 1,440 / 8,135 req/s). It improved, but it's too noisy to quote as one number.

### Two gateway instances, one client (capacity 10)

40 requests alternating between instances on ports 8080 and 8081:

| Quota store | Allowed via 8080 | Allowed via 8081 | Total allowed |
|---|---|---|---|
| memory | 10 | 10 | **20**: each instance enforces its own limit |
| redis | 5 | 5 | **10**: exactly the limit |

### Token Bucket vs Fixed Window, same workload

Both policies average **10 req/s**: Token Bucket with capacity 10 and refill 10/s, Fixed Window with 10 per 1 s window. Workload: **2,000 requests, concurrency 10**, saturating both.

| Run | Algorithm | Duration | Allowed | Predicted |
|---|---|---|---|---|
| 1 | Token Bucket | 2,465 ms | 33 | 10 + 10 × 2.47 s = 34.6 |
| 1 | Fixed Window | 3,029 ms | 38 | 10 per window started |
| 2 | Token Bucket | 2,945 ms | 39 | 39.4 |
| 2 | Fixed Window | 2,973 ms | 30 | 3 windows × 10 |
| 3 | Token Bucket | 2,701 ms | 36 | 37.0 |
| 3 | Fixed Window | 2,738 ms | 30 | 3 windows × 10 |

The totals are similar. The **shape** is not. Allowed requests per 100 ms in one run:

```
Token Bucket : 10 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1     (16 of 16 slices let traffic through)
Fixed Window : 10 0 0 0 0 0 0 0 0 0 10 0 0 0 0 0 0  (2 of 17 slices)
```

Token Bucket allows the burst, then smooths to the refill rate. Fixed Window releases the full quota at each window start and then blocks for the rest of the window. Fixed Window also allows up to **2× capacity across a window boundary**. `FixedWindowCounterTest.allowsTwiceCapacityAcrossAWindowBoundary` demonstrates this, and Token Bucket allows only 6 in the same scenario.

| Token Bucket | Fixed Window |
|---|---|
| ![Token Bucket allowed per 100 ms](docs/screenshots/simulator-token-bucket.png) | ![Fixed Window allowed per 100 ms](docs/screenshots/simulator-fixed-window.png) |

### Test evidence

- **107 automated tests**: unit, MockMvc integration, real-HTTP simulation, and Redis tests. They run against a separate `pulsegate_test` database and Redis database 1, and never touch dev data.
- **Coverage (JaCoCo):** 95.2% of lines, 91.4% of branches. The report is written to `backend/target/site/jacoco/index.html` after `mvn test`.
- **CI:** GitHub Actions runs the backend tests (with Postgres and Redis service containers) and the frontend build on every push and pull request.
- Highlights: two Redis-backed instances sharing a 500-token quota grant exactly 500 out of 2,000 concurrent attempts; 50 made-up keys from one IP produce exactly 30 database writes.

## Screenshots

| Dashboard | Clients & Policies |
|---|---|
| ![Dashboard](docs/screenshots/dashboard.png) | ![Clients and policies](docs/screenshots/clients.png) |

| Request log |
|---|
| ![Request log](docs/screenshots/request-log.png) |

## Running locally

**Prerequisites:** Java 21, Maven 3.9+, Node 20+, Docker Desktop.

```bash
# 1. Postgres (localhost:55432, plus the pulsegate_test database) and Redis (localhost:56379)
docker compose up -d

# 2. Backend on http://localhost:8080. Choose an admin token for the dashboard and admin APIs.
cd backend
export PULSEGATE_ADMIN_TOKEN=choose-a-long-random-value     # PowerShell: $env:PULSEGATE_ADMIN_TOKEN = "..."
mvn spring-boot:run

# 3. Frontend on http://localhost:5173 (second terminal)
cd frontend
npm install
npm run dev
```

Open http://localhost:5173 and **sign in with your admin token**. If you didn't set `PULSEGATE_ADMIN_TOKEN`, the backend prints a random one at startup (`Admin token for this run: adm_…`). Then:
1. **Clients & Policies** → create a policy → register a client and copy the key.
2. **Simulator** → pick the client → run a workload.
3. **Dashboard** and **Request Log** → inspect the results.

Swagger UI is at http://localhost:8080/swagger-ui.html. Click **Authorize** and enter the admin token (and a client API key for the protected endpoint).

**Shared quotas across instances (Redis):**
```bash
export PULSEGATE_LIMITER_STORE=redis
mvn spring-boot:run                          # instance 1 on :8080
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081   # instance 2
```

| Setting | Default | Purpose |
|---|---|---|
| `PULSEGATE_ADMIN_TOKEN` | random per run | Bearer token for admin APIs and the dashboard |
| `PULSEGATE_LIMITER_STORE` | `memory` | `memory` (single instance) or `redis` (shared) |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `56379` | Redis connection |
| `pulsegate.invalid-key.capacity` / `.refill-per-second` | `30` / `1.0` | Failed key attempts allowed per IP |
| `pulsegate.client-cache.ttl` | `30s` | How long a known client is cached |
| `pulsegate.events.async` / `.queue-capacity` / `.batch-size` | `true` / `10000` / `500` | Background event writing |

**Tests** (Docker must be running):
```bash
cd backend
mvn test
```

> If your Postgres volume existed before `docker/init/` was added, create the test database once:
> `docker exec pulsegate-db psql -U pulsegate -d postgres -c "CREATE DATABASE pulsegate_test OWNER pulsegate;"`

## API

Admin endpoints require `Authorization: Bearer <admin token>`. The protected endpoint requires `X-API-Key: <client key>`.

| Method | Endpoint | Auth | Purpose |
|---|---|---|---|
| GET | `/api/health` | none | Health check |
| POST | `/api/policies` | admin | Create a policy |
| GET | `/api/policies` · `/api/policies/{id}` | admin | List / view policies |
| PUT | `/api/policies/{id}` | admin | Change limits (applies on the client's next request) |
| POST | `/api/clients` | admin | Register a client. **Returns the API key once** |
| GET | `/api/clients` | admin | List clients (key prefix only) |
| GET | `/api/protected/resource` | API key | The rate-limited endpoint |
| POST | `/api/simulations` | admin | Run a workload: `{apiKey, totalRequests ≤ 5000, concurrency ≤ 100}` |
| GET | `/api/analytics/summary` | admin | Totals, latency percentiles, per-client and per-minute stats |
| GET | `/api/events?limit=&clientId=` | admin | Recent decisions, newest first (limit 1–500) |
| DELETE | `/api/events` | admin | Clear event history (quotas unaffected) |

### Examples

```bash
ADMIN='Authorization: Bearer choose-a-long-random-value'

# Create a policy: bursts of 5, refilling 1 token per second
curl -s -X POST localhost:8080/api/policies -H "$ADMIN" -H 'Content-Type: application/json' \
  -d '{"name":"free","algorithm":"TOKEN_BUCKET","capacity":5,"refillTokensPerSecond":1}'
# {"id":1,"name":"free","algorithm":"TOKEN_BUCKET","capacity":5,"refillTokensPerSecond":1.0,...}

# Register a client (save the apiKey; it is never shown again)
curl -s -X POST localhost:8080/api/clients -H "$ADMIN" -H 'Content-Type: application/json' \
  -d '{"name":"mobile-app","policyId":1}'
# {"client":{"id":1,"name":"mobile-app","apiKeyPrefix":"pg_V8x9B",...},"apiKey":"pg_V8x9B..."}

# Call the protected endpoint (no admin token: clients use their API key)
curl -i localhost:8080/api/protected/resource -H 'X-API-Key: pg_V8x9B...'
# HTTP/1.1 200
# X-RateLimit-Limit: 5
# X-RateLimit-Remaining: 4

# ...after the quota is used up
# HTTP/1.1 429
# Retry-After: 1
# X-RateLimit-Remaining: 0
# {"title":"Too Many Requests","status":429,
#  "detail":"Rate limit of 5 requests exceeded. Retry after 1s.","retryAfterSeconds":1}

# Admin API without a token
curl -i localhost:8080/api/policies
# HTTP/1.1 401
# WWW-Authenticate: Bearer
```

On Windows PowerShell, use `curl.exe` and escape the inner quotes, or use Swagger UI.

## Project structure

```
backend/src/main/java/com/pulsegate/
├── limiter/      RateLimiter, TokenBucket, FixedWindowCounter, LimitSpec, RateLimiterService
│                 (InMemory / Redis), InvalidKeyGuard, RateLimitInterceptor
├── security/     AdminAuthInterceptor (bearer token for admin APIs)
├── controller/   Policy, Client, Protected, Simulation, Analytics, Event, Health
├── service/      ApiKeyService, ClientLookupService (cache), PolicyService, ClientService,
│                 SimulationService, AnalyticsService, EventService,
│                 RequestEventRecorder (async queue) + EventBatchWriter
├── entity/       Client, RateLimitPolicy, RequestEvent, Algorithm, Decision
├── repository/   Spring Data JPA repositories (aggregates computed in SQL)
├── dto/          Request/response records
├── exception/    ProblemDetail error handling
└── config/       Interceptors + CORS, OpenAPI
backend/src/main/resources/redis/   token_bucket.lua, fixed_window.lua
frontend/src/
├── pages/        Sign in, Dashboard, Clients & Policies, Simulator, Request Log
├── components/   Layout, UI primitives, forms, modal
├── charts/       Recharts wrappers + validated outcome colours
├── services/     api.js (the only place that calls fetch), auth.js (admin session)
└── hooks/        useApi: loading/error/refresh with stale-response protection
.github/workflows/ci.yml            backend tests + frontend build
```

A 2-minute demo script and interview notes are in [docs/DEMO.md](docs/DEMO.md).

## Limitations and next steps

Already addressed: admin authentication, invalid-key flooding, multi-instance quotas (Redis), per-request database round trips (cache + async batches), and CI. Remaining:

- **Not a full reverse proxy.** It protects one sample endpoint; forwarding to arbitrary upstream services (e.g. Spring Cloud Gateway) is the next step.
- **Limits are per client only.** There are no per-endpoint, global or weighted-cost limits yet.
- **One shared admin token.** Real deployments need per-user admin accounts, roles and token rotation, plus an endpoint to deactivate clients and rotate keys.
- **Cache staleness across instances.** Other instances pick up a policy change within the 30 s cache TTL; pub/sub invalidation would make it immediate.
- **The event table grows without limit** and analytics scan all of it. It needs a retention job or time partitioning, plus pre-aggregated rollups.
- **Dropped events under extreme load** are counted and logged but not yet shown on the dashboard.
- **Schema managed by `ddl-auto=update`.** It should move to Flyway migrations.
- **Demo convenience:** the dashboard remembers created client keys in `localStorage` so the simulator can use them. Never do this with real credentials.
- The simulator shares the machine with the gateway, so its numbers are for comparison, not capacity planning.

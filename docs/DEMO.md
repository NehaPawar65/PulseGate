# PulseGate: Demo Script and Interview Notes

## Before the demo (5 minutes beforehand)

1. Start **Docker Desktop**, then `docker compose up -d`.
2. Terminal 1: set `PULSEGATE_ADMIN_TOKEN` (PowerShell: `$env:PULSEGATE_ADMIN_TOKEN = "your-token"`), then `cd backend && mvn spring-boot:run`. Wait for `Started PulsegateApplication`.
3. Terminal 2: `cd frontend && npm run dev`, then open http://localhost:5173 and **sign in with the admin token**.
4. Check that the sidebar shows **● Backend online**.
5. Optional clean slate: **Request Log → Clear history**.
6. Have Swagger open in a second tab: http://localhost:8080/swagger-ui.html.

> Keys live in the browser that created them. If the Simulator says "key not in this browser", register a fresh client in that browser.

## The 2-minute demo

| Time | Show | Say |
|---|---|---|
| 0:00 | Dashboard | "PulseGate is an API gateway that rate-limits each client. Every request is checked against the client's policy; excess traffic gets HTTP 429 instead of overloading the API." |
| 0:15 | **Clients & Policies** → New policy `smooth` (Token Bucket, capacity 10, refill 10/s) and `window` (Fixed Window, 10 per 1 s) | "Two policies with the same average rate, 10 requests a second, but different algorithms." |
| 0:35 | Register `mobile-app` on `smooth` | "The key is shown once. The server stores only its SHA-256 hash, the way GitHub and Stripe handle tokens." |
| 0:45 | Register `batch-job` on `window` | Same as above. |
| 0:55 | **Simulator** → `mobile-app`, 2000 × 10 → Run | "That's 2,000 real HTTP requests, 10 in flight at a time. Look at the Allowed chart: a burst of 10, then a steady trickle at the refill rate. Everything else got a 429." |
| 1:20 | Same workload with `batch-job` | "Same average rate, very different shape. Fixed Window releases all 10 at the start of each second and then blocks. That clumping, plus the 2× burst it allows across a window boundary, is why gateways usually prefer token bucket." |
| 1:40 | **Clients & Policies** → Edit `smooth` → capacity 50 → Simulator → run again | "Policy changes apply on the client's next request, with no restart. The limiter notices its settings changed and swaps in a fresh bucket." |
| 1:50 | **Dashboard** → **Request Log** (filter: Rate limited) | "Every decision is recorded with its latency. This is where the analytics come from." |
| 2:00 | Done | "There are 107 automated tests, running in CI on every push, including concurrency tests proving a token is never spent twice, even across two instances." |

Backup if anything breaks: Swagger → `POST /api/simulations` shows the same numbers.

## Interview questions you should be able to answer

**1. Walk me through a request.**
The interceptor hashes `X-API-Key` and loads the client and its policy in one query. A missing or unknown key gets a 401. `RateLimiterService.tryConsume` either allows the request, adding the `X-RateLimit-*` headers, or rejects it with a 429 and `Retry-After`; on rejection the controller never runs. The decision and latency are recorded in Postgres.

**2. How does the token bucket work, and why is it O(1)?**
It has two fields: `tokens` and `lastRefillNanos`. On each call, `tokens = min(capacity, tokens + elapsed × rate)`; if `tokens ≥ 1`, take one. No timer thread, no queue, no per-request history. That's constant time and constant memory per client.

**3. How do you stop two concurrent requests from spending the same token?**
`tryConsume` is `synchronized`, so refill-and-take is atomic for each bucket. The proof is a test: 50 threads, 5,000 attempts, 1,000 tokens, exactly 1,000 allowed, passing in 10 out of 10 repeated runs.

**4. And two simultaneous *first* requests from a new client?**
That's a check-then-act race: both see "no bucket" and each creates a full one. `ConcurrentHashMap.compute()` is atomic per key, so only one bucket is ever created. I call `tryConsume` outside `compute()` so the map lock is held only briefly.

**5. Why `nanoTime` and not `currentTimeMillis`?**
Wall-clock time can jump backwards (NTP sync), which would create negative elapsed time or free tokens. `nanoTime` is monotonic.

**6. Token Bucket vs Fixed Window?**
They can have the same average rate, but Token Bucket allows a bounded burst and then smooths to the refill rate. Fixed Window releases the whole quota at each window start and allows up to 2× capacity across a boundary. I measured both on the same workload: Token Bucket let traffic through in 16 of 16 time slices, Fixed Window in 2 of 17. Fixed Window's advantage is simplicity, and its counter is easy to keep in Redis with `INCR` + `EXPIRE`.

**7. What happens if the database goes down?**
The limit decision is made in memory first, so limits are still enforced. The event write fails, is logged, and the response is unchanged. I chose to lose analytics rather than fail open. Key lookups do need the database, so new requests would fail with a 500; a key cache would fix that.

**8. How did you make it work across several instances?**
In-memory buckets aren't shared, so with two instances a client would get double its quota. I measured exactly that: capacity 10, two instances, 20 allowed. I moved the state to Redis and run refill-and-take as **one Lua script**. Redis executes scripts one at a time and I use its own clock (`TIME`), so instances can't interleave and server clock skew doesn't matter. With Redis the same test allows exactly 10. If Redis goes down, the gateway falls back to per-instance limits rather than switching limits off.

**9. How did you reduce latency?**
The limit check takes microseconds, but every request also made two DB round trips (key lookup + event insert). I added a Caffeine cache for key → client + policy (cleared on policy edits) and moved event writes to a bounded queue drained by a background thread in JDBC batches. When the queue is full, events are dropped and counted, never blocking requests. Same workload before and after: server p50 went from **25.2 ms to 0.22 ms**, and p95 from 134 ms to 7 ms, with all 9,000 events still recorded.

**10. Why hash API keys with SHA-256 and not bcrypt?**
bcrypt is slow and salted on purpose, to protect guessable passwords. A 192-bit random key can't be guessed, and a deterministic hash lets me look the key up through an index. With bcrypt I'd have to compare against every row.

**11. How do you know the measurements are real?**
The simulator sends real HTTP through the full gateway path, and the counts match the theory: 10 + 10 × 2.22 s ≈ 32 predicted, 32 allowed. The README states the hardware and that the load generator shares the JVM, so the numbers are comparative, not capacity claims.

**12. What would you do next?**
Turn it into a real reverse proxy (forwarding to upstream services), per-endpoint and weighted limits, per-user admin accounts with roles, pub/sub cache invalidation across instances, an event retention job, Flyway migrations, and a Sliding Window Counter algorithm.

**13. How do you stop someone flooding the gateway with made-up keys?**
Each unknown key costs a DB lookup and an insert, so I limit failed attempts **per IP** with a token bucket (30, refilling 1/s). Once used up, that IP gets 429 before any database work. A test sends 50 bad keys from one IP and checks that exactly 30 reached the database.

## Resume entry (every number below was measured in this project)

**PulseGate: API Traffic Gateway** | Java 21, Spring Boot, PostgreSQL, React, Recharts
- Built an API gateway with per-client **Token Bucket** and **Fixed Window** rate limiting, returning HTTP 429 with `Retry-After`. Proved thread safety with concurrency tests: 50 threads, 5,000 requests, exactly 1,000 tokens granted.
- Stored SHA-256-hashed API keys, policies, and request events in PostgreSQL. Computed latency percentiles in SQL for a live React analytics dashboard, and kept limits enforced even when event logging fails.
- Built a concurrent traffic simulator that compares the algorithms on the same workload, showing Token Bucket smoothing traffic to its refill rate while Fixed Window releases quota in bursts. Backed by 107 automated tests at 95% line coverage, running in GitHub Actions CI.

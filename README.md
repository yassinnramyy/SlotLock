# SlotLock

A multi-tenant booking platform built to answer one specific question properly: **when two
people try to book the same slot at the same instant, how do you guarantee exactly one of
them succeeds — and what does it actually cost you to guarantee that?**

Everything else in the system (multi-tenancy, JWT auth, idempotent slot generation) exists to
support that core problem, not the other way around.

## Architecture

- **Spring Boot 3 / Java 21**, MySQL 8 (InnoDB) with Flyway migrations, RabbitMQ (outbox +
  waitlist events, in progress — see Roadmap)
- **Stateless JWT auth**, tenant context propagated per-request via a `ThreadLocal` set by a
  servlet filter, read by application code through `SecurityUtils`
- **Tiered role model**: `SUPER_ADMIN` (platform-level, creates tenants and tenant admins) →
  `ADMIN` (manages resources/staff within one tenant) → `STAFF` / `CUSTOMER`. Nobody can
  self-assign a role at registration — that's a deliberate lockdown, not an oversight.
- **Domain modules** (`application/`, `masterdata/`, `booking/`, `outbox/`), each layered
  internally by technical concern (`controller/`, `dto/`, `entity/`, `mapper/`, `repository/`,
  `service/`). Cross-module references (`tenantId`, `resourceId`, etc.) are plain foreign-key
  fields, never JPA relationships — a deliberate simplicity tradeoff.
- Every tenant-scoped lookup uses the caller's own `tenantId` from the security context, never
  a client-supplied one, and a resource that exists but belongs to a different tenant returns
  the same `404` as one that doesn't exist at all — no status code ever confirms another
  tenant's data exists.

## The concurrency problem

`Slot.status` is a simple `OPEN` / `BOOKED` flag. The obvious way to book one — read the
status, check it's `OPEN`, write `BOOKED` — has a gap between the read and the write. Under
concurrent load, two requests can both read `OPEN` before either has written anything back.
Both then proceed. That's a double-booking, and no amount of wrapping the method in
`@Transactional` fixes it on its own, because both transactions are individually correct —
each one really did see a genuinely committed `OPEN` value. The bug lives in the gap between
them, not inside either one.

### Method

A test fires 50 concurrent booking requests at a single open slot (`CountDownLatch`-synchronized
so all threads start as close to simultaneously as possible) and records exactly what happens
to each one, rather than just asserting a final count. This was run against three
implementations of the same booking operation:

- **Naive** — plain unlocked read, plain `save()` on the write.
- **Pessimistic** — `SELECT ... FOR UPDATE` at read time, so a second transaction's read
  physically blocks until the first commits.
- **Optimistic** — plain unlocked read; the write is a conditional `UPDATE ... WHERE
  version = ?` (backed by JPA's `@Version`), and a version mismatch on write is caught and
  translated into a clean conflict response.

### Results (50 concurrent requests, one slot)

| | Successes | Clean conflicts | Deadlocks (MySQL 1213) | Unhandled |
|---|---|---|---|---|
| Naive | 1 | 40 | 9 | 0 |
| Pessimistic (`SELECT ... FOR UPDATE`) | 1 | 49 | 0 | 0 |
| Optimistic (`@Version`, caught) | 1 | 49 | 0 (5/5 runs) | 0 |

All three approaches produced exactly one successful booking, every run — none of them
double-booked the slot. The naive version's write already carried an accidental safety net
(a `@Version` column added for a later phase, quietly enforced by Hibernate on every write
regardless of whether the code "intended" to use it), which is *why* it capped at one success
instead of failing open. What it didn't have was a *clean* failure mode: roughly a fifth of
the naive run's losers didn't get a polite "someone booked this already" — they crashed with
a raw MySQL deadlock (`CannotAcquireLockException`), because ~10 threads read `OPEN` in the
same narrow window and then all raced toward `UPDATE` on the same row with no coordination,
which is exactly the condition InnoDB's deadlock detector exists to break up.

**The interesting finding wasn't "optimistic beat pessimistic" — they tied.** Both eliminated
the deadlocks entirely. Digging into *why* the naive version deadlocked and the optimistic one
didn't, despite both doing an unlocked read, pointed at something more specific than the
locking strategy itself: the naive path's write went through a plain `save()`, which Hibernate
is free to defer and batch together with the subsequent `Booking` insert at commit time — and
batched flushes don't necessarily preserve call order, which opens the door to a cross-table
lock cycle (slot row vs. the booking table's unique-index insert point) under enough
concurrent pressure. The optimistic implementation was required to use `saveAndFlush()` instead
(the only way to make the version-conflict exception catchable inside the method at all),
which forces that write out immediately, alone, decoupled from the booking insert — closing
off that specific cycle. That's the most plausible explanation given the code, not something
verified against InnoDB's internal lock-wait graph, which isn't available after the fact.

The real, general tradeoff between the two strategies never actually showed up in this test,
and structurally couldn't from a single burst against one row — it's about behavior under
*sustained* load: pessimistic locking makes the 49 losers wait in an orderly queue; optimistic
locking lets all 49 race freely and only tells them they lost after they've already done real
work that gets discarded. Under light contention that difference is invisible. Under heavy,
sustained contention on the same row, it isn't.

## API overview

| Endpoint | Access |
|---|---|
| `POST /api/auth/register` | Public — always creates a `CUSTOMER` |
| `POST /api/auth/login` | Public |
| `POST /api/auth/admins` | `SUPER_ADMIN` — creates a tenant admin |
| `POST /api/auth/staff` | `ADMIN` — creates staff under caller's tenant |
| `POST /api/tenants` | `SUPER_ADMIN` |
| `GET /api/tenants/slug/{slug}` | Public |
| `POST /api/resources` | `ADMIN` / `SUPER_ADMIN` |
| `GET /api/resources/{id}/slots?from=&to=` | Authenticated — idempotent slot generation |
| `POST /api/bookings` | Authenticated — pessimistic locking |
| `POST /api/bookings/optimistic` | Authenticated — optimistic locking |
| `POST /api/bookings/{id}/cancel` | Owner or `ADMIN` |

A Postman collection covering all of the above is in `postman/`.

## Running locally

```bash
docker compose up -d      # MySQL on :3307, RabbitMQ on :5672 (management UI on :15672)
./mvnw -q compile
./mvnw spring-boot:run    # :8080
```

Copy `.env.example` to `.env` and set `JWT_SECRET` to a real value — every other variable
already has a working local-dev default in `application.yml` that matches
`docker-compose.yml`.

## Roadmap

- [x] JWT auth, tiered role/user creation, tenant-scoped security context
- [x] Resource + availability window CRUD, idempotent slot generation
- [x] Concurrency-safe booking — proven correct under load, two strategies compared
- [ ] Transactional outbox pattern for reliable notification delivery via RabbitMQ
- [ ] Event-driven waitlist promotion on cancellation
- [ ] Frontend
- [ ] Deployment

## A note on how this was built

Implementation was done with Claude Code, working from a written spec and one scoped task at
a time. The concurrency methodology — the test design, the before/after numbers, the
deadlock investigation, and the corrected understanding of what the results actually showed —
was worked through and verified directly rather than taken on faith from generated output.

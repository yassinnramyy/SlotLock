# SlotLock — CLAUDE.md

Multi-tenant, concurrency-safe booking platform. Spring Boot 3 / Java 21, base package `com.slotlock`.
Portfolio project built to demonstrate correct handling of race conditions under concurrent load
(double-booking prevention), the transactional outbox pattern, and event-driven waitlist promotion.

This repo is **public on GitHub**. Never hardcode secrets, passwords, or keys in any committed
file. Everything sensitive comes from environment variables with obviously-fake local-dev
fallbacks (e.g. `${JWT_SECRET:local-dev-only-insecure-default-change-me}`). `.env` is gitignored;
`.env.example` documents variable names only, no real values.

## Tech stack

- Spring Boot 3, Java 21
- MySQL 8 (InnoDB) + Flyway migrations (`src/main/resources/db/migration`)
- RabbitMQ (outbox relay + waitlist promotion events — not wired up yet, see Status)
- Spring Security + JWT (stateless, no sessions)
- Lombok (`@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder` on entities)
- MapStruct (`@Mapper(componentModel = "spring")` on all mappers) — remember `lombok-mapstruct-binding`
  as an annotation processor path, or Lombok-generated getters won't be visible to MapStruct
- DTOs are Java records, not classes
- Docker Compose for local MySQL + RabbitMQ (`docker-compose.yml` at project root)
- Postman collection at `postman/SlotLock.postman_collection.json` for manual testing

## Module structure

Domain modules under `com.slotlock`, each internally layered by technical concern
(`controller/`, `dto/request`+`dto/response`, `entity/`, `enums/`, `mapper/`, `repository/`,
`service/`+`service/impl/`). This mirrors an existing project of the developer's
(`eyes-clinic`) on purpose — keep new code consistent with this pattern rather than
introducing a different structure.

- **`application/`** — cross-cutting infra: auth, JWT, tenant context, exception handling.
  Nested `application/config/` holds all `@Configuration` classes AND the JWT/tenant
  infrastructure (`JwtTokenProvider`, `JwtAuthenticationFilter`, `TenantContext`, `TenantFilter`,
  `TenantFilterInterceptor`, `SecurityUtils`, `CustomAuthenticationEntryPoint`,
  `MethodSecurityConfig`) — this is intentional, matches `eyes-clinic`'s layout, don't move
  these into `service/` or `util/`.
- **`masterdata/`** — `Tenant` entity/CRUD. Platform-level, created by `SUPER_ADMIN` only.
- **`booking/`** — the core domain: `Resource`, `AvailabilityWindow`, `Slot`, `Booking`.
- **`outbox/`** — nested infra sub-module (mirrors `eyes-clinic`'s `audit/` pattern),
  currently empty, not yet implemented (see Status).

Note one inconsistency that exists and should NOT be propagated further: `application/service/`
uses a capitalized `Impl/` package, while `masterdata/` and `booking/` correctly use lowercase
`impl/`. Leave the existing one alone for now; always use lowercase `impl` in any new code.

Entities: `Tenant`, `Resource`, and `Booking` extend `application/entity/BaseEntity.java`
(id + createdAt) because their tables have a `created_at` column. `AvailabilityWindow` and
`Slot` do NOT extend it — their tables have no `created_at` column, they define their own
bare `@Id`. `Slot` and `Booking` both carry a `@Version` column for optimistic locking.

Cross-module foreign keys (`tenantId` on `User`/`Resource`, `resourceId` on `AvailabilityWindow`/
`Slot`, `slotId`/`customerId` on `Booking`) are plain `Long` fields, never JPA `@ManyToOne`
relationships. This is a deliberate simplicity choice, keep it consistent.

## Security & multi-tenancy — critical rules

- Roles: `SUPER_ADMIN` (platform-level, creates tenants and tenant admins), `ADMIN` (manages
  resources/staff within their own tenant), `STAFF`, `CUSTOMER`.
- User creation is tiered, NOT self-service role assignment: `POST /api/auth/register` is
  public and always creates a `CUSTOMER`. `POST /api/auth/admins` requires `SUPER_ADMIN` and
  creates a tenant admin for a given `tenantId`. `POST /api/auth/staff` requires `ADMIN` and
  creates staff under the caller's own tenant. Never reintroduce a client-suppliable `role`
  field on the public registration endpoint.
- **`tenantId` is NEVER read from client-supplied request data when creating or scoping
  resources.** Always `com.slotlock.application.config.SecurityUtils.getCurrentTenantId()`.
  This is the multi-tenancy security boundary — a tenant must never be able to act on another
  tenant's data by guessing or supplying a different `tenantId`.
- When a lookup fails because a resource belongs to a different tenant, throw the SAME
  `NOT_FOUND` exception you'd throw if it didn't exist at all — never `FORBIDDEN`. Don't leak
  the existence of other tenants' data via status codes. This pattern is used consistently in
  `DefaultResourceService`, `DefaultBookingService`, etc. — follow it in new code.
- Exception handling: throw `BusinessLogicViolationException` (with explicit `HttpStatus` +
  `ApiErrorCodeEnum`) or `ApiException` directly for ad hoc cases; `AppExceptionHandler`
  converts these to a consistent JSON error shape. Don't invent a new exception-response shape.

## Booking-specific conventions

- **Tenant-scoped vs. unscoped callers.** `ADMIN`/`STAFF` are scoped to their own tenant for
  everything (private helper `isTenantScopedCaller()` in `DefaultResourceService` and
  `DefaultBookingService` returns true for these two roles only). `CUSTOMER` is deliberately
  tenant-less — customers browse and book across every tenant/category, so any code path a
  customer hits must never call `SecurityUtils.getCurrentTenantId()` unconditionally (it throws
  for a tenant-less caller). Where a tenant-scoped caller needs their own tenant and an unscoped
  one needs an explicit one, see `DefaultResourceService.resolveBrowseTenantId(Long)` — the
  pattern to follow: tenant-scoped callers use their own tenant, others must supply
  `?tenantId=` explicitly (400 if they don't, there's no implicit fallback).
- **`SUPER_ADMIN` access is browse/support-only, not self-booking.** They can view resources,
  slots, and any individual booking across tenants (`isOwnerOrAdmin()` already treats them as
  admin-equivalent), and cancel/delete bookings — but `book()`/`bookOptimistic()` deliberately
  exclude `SUPER_ADMIN` from `@PreAuthorize`, because without an on-behalf mechanism, a
  `SUPER_ADMIN` booking would silently be created under their own user id, which is wrong (they
  aren't a customer). Do not add `SUPER_ADMIN` to those two endpoints without also building
  on-behalf booking (see next point).
- **On-behalf booking (staff/admin booking for a customer, e.g. a phone call) is a DEFERRED
  design, not implemented.** A `customerId`-on-`BookingRequest` approach was designed and then
  explicitly rejected — a single field whose meaning (required / optional-with-fallback /
  ignored) silently depends on the caller's role was judged too error-prone (e.g. `ADMIN`
  omitting `customerId` would silently self-book instead of failing, and there's no audit trail
  distinguishing "customer booked this themselves" from "staff booked it for them"). If this
  gets built, the intended direction is a **separate endpoint** (e.g.
  `POST /api/bookings/on-behalf`, `ADMIN`/`STAFF`/`SUPER_ADMIN` only) with `customerId` as a
  real `@NotNull` field enforced by bean validation, always recording a `createdByUserId`
  alongside `customerId` — not a conditional branch on the existing `book()`/`bookOptimistic()`.
- **Idempotency keys are strict, by design — reusing one always returns that exact row as-is,
  regardless of its current status.** A key whose booking was since cancelled does NOT release
  itself for a fresh attempt; calling `book()`/`bookOptimistic()` again with the same key
  returns the `CANCELLED` row, not a new booking. This matches Stripe-style idempotency
  semantics: a key represents one permanent attempt, not "the current state of this resource for
  this client." A client that wants to book again after cancelling must send a NEW
  `idempotencyKey`. Do not make the cancelled-releases-the-key behavior (Option B) the default
  without it being a deliberate, separate decision — see comments in `book()`/`bookOptimistic()`
  in `DefaultBookingService`.

## Build & run

```
docker compose up -d          # MySQL on :3307, RabbitMQ on :5672 (mgmt UI :15672)
./mvnw -q compile             # verify it builds
./mvnw spring-boot:run        # run the app, :8080
```

`.env` needs only `JWT_SECRET` set to a real value locally — every other variable
(`DB_URL`, `DB_USERNAME`, `RABBITMQ_*`, etc.) already has a working default in
`application.yml` that matches `docker-compose.yml`.

## Status (update this section as phases complete)

**Done:**
- `application/` — full JWT auth, tiered user creation, tenant-scoped security context.
- `masterdata/` — `Tenant` CRUD.
- `booking/` — `Resource` + `AvailabilityWindow` CRUD, idempotent slot generation from
  availability windows, and booking with pessimistic AND optimistic locking both implemented as
  two deliberately separate,
  comparable code paths on `BookingService`: `book()` (pessimistic) and `bookOptimistic()`
  (optimistic). Neither replaces the other. `POST /api/bookings` uses `book()`;
  `POST /api/bookings/optimistic` uses `bookOptimistic()`.
- `src/test/java/com/slotlock/booking/BookingConcurrencyTest.java` — 50-thread concurrent
  booking test harness (`runConcurrentBookingRace`, parameterized by which booking method to
  call), using the CountDownLatch ready/start/done pattern, run once per locking strategy
  against its own fresh slot fixture. Manually seeds `SecurityContextHolder`/`TenantContext`
  per thread (see class comments — these are ThreadLocal and normally only populated by
  `JwtAuthenticationFilter`/`TenantFilter`, which this test bypasses by calling the booking
  service directly).

**Concurrency results — naive vs. pessimistic vs. optimistic (50 threads, 1 open slot):**

| | successes | clean conflicts | unhandled deadlocks (1213) | other unexpected |
|---|---|---|---|---|
| Naive (plain `findById`, plain `save`, no version handling) | 1 | 40 | 9 | 0 |
| Pessimistic (`findByIdForUpdate`, `SELECT ... FOR UPDATE`) | 1 | 49 | 0 | 0 |
| Optimistic (plain `findById`, `saveAndFlush` + catch `ObjectOptimisticLockingFailureException`) | 1 | 49 | 0 (5/5 runs) | 0 |

None of the three ever double-booked (`Slot.version` alone already prevented that, even in the
naive version — see below). What differs is HOW CLEANLY each handles losers: naive leaked raw
DB deadlocks to 9/50 callers as unhandled exceptions; pessimistic and optimistic both convert
100% of losers into a clean `SlotConflictException`.

The optimistic result is notable: the prediction going in was that deadlocks would persist
(same blind-concurrent-`UPDATE` write pattern as the naive version, only the read strategy
changed), but 5/5 runs showed zero. Most likely explanation: `bookOptimistic()` uses
`saveAndFlush()` (required so the version-check exception is catchable in this method at all),
which forces each thread's `UPDATE` to hit the DB immediately and in isolation, right after its
read. The naive version's plain `save()` left that `UPDATE` to be flushed at transaction commit,
batched together with the `Booking` `INSERT` — very likely widening the window in which many
transactions' lock requests overlap, which is what deadlock formation depends on. Not proven via
InnoDB internals instrumentation, just the most plausible code-level explanation for a real,
repeatable difference — deadlocks under blind concurrent writes are fundamentally a timing/load
phenomenon, so "0/5 here" is evidence this implementation is less deadlock-prone, not proof
it's architecturally immune.

**Not started:**
- `outbox/` — transactional outbox pattern for reliable notification delivery via RabbitMQ.
- Waitlist join endpoint + `SLOT_OPENED` promotion consumer (reuses `DefaultBookingService`'s
  eventual locked booking logic, not a separate path).
- Deployment (target: Elastic Beanstalk, matching an earlier project of the developer's).

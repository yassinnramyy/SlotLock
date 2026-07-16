package com.slotlock.booking;

import com.slotlock.application.config.TenantContext;
import com.slotlock.application.entity.User;
import com.slotlock.application.enums.UserRoleEnum;
import com.slotlock.application.exception.SlotConflictException;
import com.slotlock.application.repository.UserRepository;
import com.slotlock.booking.dto.request.BookingRequest;
import com.slotlock.booking.entity.Resource;
import com.slotlock.booking.entity.Slot;
import com.slotlock.booking.enums.SlotStatus;
import com.slotlock.booking.repository.BookingRepository;
import com.slotlock.booking.repository.ResourceRepository;
import com.slotlock.booking.repository.SlotRepository;
import com.slotlock.booking.service.BookingService;
import com.slotlock.masterdata.entity.Tenant;
import com.slotlock.masterdata.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Fires 50 concurrent booking attempts at the SAME open slot and checks how many actually
 * succeed. Exactly one should — booking a slot means claiming exclusive ownership of it, so
 * 50 threads racing for it is the whole point of the test: it should behave like 50 people
 * mashing "Book Now" on the same appointment at the same instant.
 * <p>
 * Two independent code paths are exercised, each against its own fresh slot, so the numbers
 * are directly comparable:
 * <ul>
 *   <li>{@code DefaultBookingService.book()} — pessimistic locking
 *       ({@code SlotRepository.findByIdForUpdate}, {@code SELECT ... FOR UPDATE}).</li>
 *   <li>{@code DefaultBookingService.bookOptimistic()} — optimistic locking (plain unlocked
 *       read, relies on {@code Slot.version} and a caught
 *       {@code ObjectOptimisticLockingFailureException} at write time).</li>
 * </ul>
 * Run against the fully naive version that predates both of these (plain {@code findById} with
 * no version-conflict handling at all), this same shape of test is what proved that flow broken
 * — see CLAUDE.md Status for those historical numbers.
 */
@SpringBootTest
class BookingConcurrencyTest {

    private static final int THREAD_COUNT = 50;

    @Autowired
    private BookingService bookingService;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private ResourceRepository resourceRepository;
    @Autowired
    private SlotRepository slotRepository;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private UserRepository userRepository;

    /** One tenant + resource + OPEN slot + THREAD_COUNT fixture customers, ready to race over. */
    private record Fixture(Long tenantId, Long resourceId, Long slotId, List<Long> customerIds) {
    }

    /** Outcome of one 50-thread race: raw counts plus every non-conflict exception, uncategorized. */
    private record RaceResult(int successCount, int conflictCount, List<Throwable> unexpectedFailures,
                               boolean finishedInTime) {
    }

    private Fixture fixture;

    @BeforeEach
    void setUp() {
        fixture = createFixture();
    }

    @AfterEach
    void tearDown() {
        deleteFixture(fixture);
    }

    // Plain repository.save() calls, no @Transactional on this method or the test class.
    // JpaRepository's save() is itself @Transactional (see SimpleJpaRepository), so with no
    // surrounding transaction here each save opens and COMMITS immediately. That commit is what
    // matters: the 50 worker threads racing over the resulting slot each open their own DB
    // connection, so the fixture rows must be visible to connections other than this one before
    // the threads start. If this method were wrapped in @Transactional instead, the rows would
    // sit uncommitted on this thread's connection and every worker thread would either see no
    // slot at all or block forever waiting on a row that, from their connection's point of view,
    // doesn't exist yet.
    private Fixture createFixture() {
        long uniqueSuffix = System.nanoTime();

        Tenant tenant = tenantRepository.save(Tenant.builder()
                .name("Concurrency Test Tenant")
                .slug("concurrency-test-" + uniqueSuffix)
                .build());
        Long tenantId = tenant.getId();

        Resource resource = resourceRepository.save(Resource.builder()
                .tenantId(tenantId)
                .name("Concurrency Test Resource")
                .slotDurationMinutes(30)
                .build());
        Long resourceId = resource.getId();

        Slot slot = slotRepository.save(Slot.builder()
                .resourceId(resourceId)
                .startAt(LocalDateTime.now().plusDays(1))
                .endAt(LocalDateTime.now().plusDays(1).plusMinutes(30))
                .status(SlotStatus.OPEN)
                .build());
        Long slotId = slot.getId();

        // Deviation from a literal "fake customerId, doesn't need to be a real User" fixture:
        // bookings.customer_id has a NOT NULL foreign key to users(id) (V4__create_bookings.sql,
        // fk_bookings_customer). A synthetic id like 1000L + threadIndex with no backing row
        // would make every single booking call fail on that FK constraint — a real DB error, not
        // a SlotConflictException — which would sink the test for the wrong reason and hide the
        // race entirely. So each thread gets a minimal real User row instead; nothing about these
        // users is ever exercised except their id (no login, no password check happens — auth is
        // injected directly into SecurityContextHolder below, bypassing the HTTP/JWT layer
        // completely).
        List<User> customers = new ArrayList<>();
        for (int i = 0; i < THREAD_COUNT; i++) {
            customers.add(User.builder()
                    .tenantId(tenantId)
                    .email("concurrency-test-" + uniqueSuffix + "-" + i + "@example.com")
                    .passwordHash("not-a-real-hash-never-used-for-login")
                    .role(UserRoleEnum.CUSTOMER)
                    .build());
        }
        List<Long> customerIds = userRepository.saveAll(customers).stream().map(User::getId).toList();

        return new Fixture(tenantId, resourceId, slotId, customerIds);
    }

    // Cleanup only — not part of the concurrency mechanism under test. Nothing in createFixture()
    // ran inside a transaction that could be rolled back automatically, so we tear down by hand,
    // in FK-safe order (bookings before slot/users, slot before resource, resource before
    // tenant), to keep the shared dev DB clean across repeated runs.
    private void deleteFixture(Fixture f) {
        bookingRepository.findAll().stream()
                .filter(booking -> booking.getSlotId().equals(f.slotId()))
                .forEach(bookingRepository::delete);
        slotRepository.deleteById(f.slotId());
        userRepository.deleteAllById(f.customerIds());
        resourceRepository.deleteById(f.resourceId());
        tenantRepository.deleteById(f.tenantId());
    }

    @Test
    void concurrentBookingAttemptsOnSameSlot_onlyOneShouldSucceed() throws InterruptedException {
        RaceResult result = runConcurrentBookingRace(fixture, bookingService::book);

        assertTrue(result.finishedInTime(), "Booking threads did not finish within the 30s timeout");
        if (!result.unexpectedFailures().isEmpty()) {
            fail(describeFailures(result.unexpectedFailures()));
        }
        assertEquals(1, result.successCount(), "Expected exactly one thread to win the race for the slot");
        assertEquals(THREAD_COUNT - 1, result.conflictCount(), "Expected the other 49 threads to see a conflict");
    }

    @Test
    void concurrentOptimisticBookingAttemptsOnSameSlot_onlyOneShouldSucceed() throws InterruptedException {
        // A brand new fixture, NOT the one from setUp()/fixture — the pessimistic test above
        // races over that one via its own @Test method, and both tests can run in the same class
        // instance (JUnit 5 gives each @Test method a fresh instance by default, but even if it
        // didn't, reusing an already-BOOKED slot from another test would make this test measure
        // nothing). Cleaned up locally in a finally block since this fixture isn't the one
        // tearDown() knows about.
        Fixture optimisticFixture = createFixture();
        try {
            RaceResult result = runConcurrentBookingRace(optimisticFixture, bookingService::bookOptimistic);

            assertTrue(result.finishedInTime(), "Booking threads did not finish within the 30s timeout");
            // The ONLY guarantee bookOptimistic() has to provide: no double-booking. Unlike the
            // pessimistic test, we deliberately do NOT assert on the conflict/unexpected-failure
            // split here, and do NOT assert zero unexpected failures — whether concurrent blind
            // UPDATEs against the same row still produce MySQL deadlocks under this strategy is
            // an open question this test exists to answer with real data, not a guess baked into
            // an assertion.
            assertEquals(1, result.successCount(), "Expected exactly one thread to win the race for the slot");

            // Breakdown of everything else that happened, by exception type, purely for
            // reporting — see the class-level Javadoc / final report for what these numbers mean.
            Map<String, Long> unexpectedByType = result.unexpectedFailures().stream()
                    .collect(Collectors.groupingBy(t -> t.getClass().getName(), Collectors.counting()));

            System.out.println("=== bookOptimistic() concurrency breakdown ===");
            System.out.println("successCount (should be 1): " + result.successCount());
            System.out.println("conflictCount (clean SlotConflictException): " + result.conflictCount());
            if (unexpectedByType.isEmpty()) {
                System.out.println("unexpected exceptions: none");
            } else {
                unexpectedByType.forEach((type, count) -> System.out.println("unexpected: " + type + " x" + count));
            }
            int accountedFor = result.successCount() + result.conflictCount() + result.unexpectedFailures().size();
            System.out.println("total accounted for: " + accountedFor + " / " + THREAD_COUNT);
        } finally {
            deleteFixture(optimisticFixture);
        }
    }

    private RaceResult runConcurrentBookingRace(
            Fixture f, Function<BookingRequest, BookingService.BookingResult> bookingCall)
            throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        // Three-latch pattern:
        //  - readyLatch:  each thread counts this down once it has finished per-thread setup
        //                 (see below) and is parked waiting on startLatch. The main thread
        //                 waits for ALL 50 to reach that point before firing the start gun.
        //  - startLatch:  a single count released all at once (countDown() called exactly once,
        //                 by the main thread) so every worker thread's await() returns in
        //                 essentially the same instant, instead of threads trickling into the
        //                 booking call one at a time as the pool schedules them. Without this,
        //                 an unsafe implementation might not even race — the bug depends on
        //                 genuine overlap between the read and the write of Slot.status.
        //  - doneLatch:   lets the main thread know all 50 attempts have finished (success,
        //                 conflict, or otherwise) before it reads the counters and returns.
        CountDownLatch readyLatch = new CountDownLatch(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);

        // AtomicInteger, not a plain int field: 50 threads will each do successCount++ (or
        // conflictCount++) with no external synchronization. A plain int++ is NOT one operation
        // — it's read-current-value, add 1, write-new-value, three separate steps that can
        // interleave across threads. Two threads can both read the same current value before
        // either writes back, and one increment gets lost. That is the exact same "read a value,
        // then act on a decision made from that now-stale value" shape as the Slot.status race
        // this whole test exists to prove — so using a plain int here would risk a second,
        // accidental race condition inside the test that's supposed to be measuring the first
        // one. AtomicInteger.incrementAndGet() is a single atomic read-modify-write (backed by a
        // CPU compare-and-swap loop), so it can't lose updates no matter how many threads hit it
        // simultaneously.
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        // Anything caught below that ISN'T a SlotConflictException gets recorded here instead of
        // failing the test from inside the worker thread. executor.submit(Runnable) hands back a
        // Future whose result/exception nobody in this test ever reads via .get() — by design,
        // since we want all 50 threads to run to completion and be counted, not have the pool's
        // bookkeeping stop at the first one that throws. That means a bare fail(...) called from
        // inside the lambda would throw an AssertionError that gets captured by the Future and
        // then silently discarded, NOT surfaced as a test failure — exactly the "silently
        // absorbed" outcome this test is explicitly supposed to avoid. CopyOnWriteArrayList is
        // the thread-safe collection here for the same reason AtomicInteger is used above:
        // multiple threads add to it concurrently with no external synchronization, so a plain
        // ArrayList (not thread-safe for concurrent writes) could corrupt its internal state or
        // lose entries.
        List<Throwable> unexpectedFailures = new CopyOnWriteArrayList<>();

        for (int i = 0; i < THREAD_COUNT; i++) {
            int threadIndex = i;
            Long customerId = f.customerIds().get(threadIndex);

            executor.submit(() -> {
                try {
                    // CRITICAL per-thread setup. SecurityUtils.getCurrentUserId()/
                    // getCurrentTenantId() and TenantContext.get() all read from ThreadLocal
                    // storage. In a real request, JwtAuthenticationFilter and TenantFilter
                    // populate that ThreadLocal exactly once, early in the filter chain, before
                    // the request ever reaches BookingController/DefaultBookingService. This test
                    // skips the HTTP layer entirely and calls the booking service directly from a
                    // thread-pool thread that Spring Security/the tenant filter have never seen —
                    // so nothing populates that ThreadLocal unless we do it ourselves, per
                    // thread, right here. Skip this and SecurityUtils.getCurrentUserId() throws
                    // IllegalStateException ("No authenticated user") instead of testing anything
                    // about booking.
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(customerId, null,
                                    List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
                    TenantContext.set(f.tenantId());

                    readyLatch.countDown();
                    startLatch.await();

                    bookingCall.apply(new BookingRequest(f.slotId(), "test-key-" + threadIndex));
                    successCount.incrementAndGet();
                } catch (SlotConflictException e) {
                    // Expected outcome for every thread except whichever one wins the race. Every
                    // locking strategy under test throws this same exception for its losers —
                    // they differ in WHY it gets thrown, not in what the caller sees. See the
                    // final report for the SQL-level distinction.
                    conflictCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    unexpectedFailures.add(e);
                } catch (Exception e) {
                    // Deliberately NOT lumped in with SlotConflictException above. Any other
                    // exception (a DB constraint violation, a raw optimistic-lock failure that
                    // leaked through uncaught, a genuine MySQL deadlock, an NPE, etc.) means
                    // something happened that isn't the clean "you lost the race" outcome
                    // SlotConflictException represents. Silently counting it as a "conflict"
                    // would hide exactly the evidence this test exists to surface. Recorded here
                    // instead, so the caller can decide what to do with it — fail loudly (the
                    // pessimistic test) or report the breakdown (the optimistic test, which is
                    // explicitly testing for this).
                    unexpectedFailures.add(e);
                } finally {
                    // Must run unconditionally (the booking call throws for 49 of 50 threads by
                    // design) and on the SAME thread that set the context, because the executor's
                    // threads are pooled and reused across tasks. If a thread doesn't clear its
                    // ThreadLocal here, the next unrelated task the pool schedules onto that same
                    // physical thread would silently inherit this task's leftover
                    // authentication/tenant — i.e. one customer's identity leaking into another's
                    // request. That's a correctness/security bug class in its own right,
                    // independent of the booking race this test targets.
                    SecurityContextHolder.clearContext();
                    TenantContext.clear();
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        boolean finishedInTime = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        return new RaceResult(successCount.get(), conflictCount.get(), unexpectedFailures, finishedInTime);
    }

    private static String describeFailures(List<Throwable> failures) {
        // Surface every unexpected exception, not just the first, and print each one's class +
        // message — with 50 threads hitting the same row, this is frequently more than one kind
        // of failure at once, and seeing all of them together is what actually explains what's
        // going wrong.
        StringBuilder detail = new StringBuilder(failures.size() + " unexpected exception(s) from booking threads:");
        for (Throwable t : failures) {
            detail.append("\n  - ").append(t.getClass().getName()).append(": ").append(t.getMessage());
        }
        return detail.toString();
    }
}

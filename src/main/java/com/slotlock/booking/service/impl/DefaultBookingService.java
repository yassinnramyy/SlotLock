package com.slotlock.booking.service.impl;

import com.slotlock.application.config.SecurityUtils;
import com.slotlock.application.exception.ApiErrorCodeEnum;
import com.slotlock.application.exception.ApiException;
import com.slotlock.application.exception.BusinessLogicViolationException;
import com.slotlock.application.exception.SlotConflictException;
import com.slotlock.outbox.service.OutboxService;
import com.slotlock.booking.dto.request.BookingRequest;
import com.slotlock.booking.dto.response.BookingResponse;
import com.slotlock.booking.entity.Booking;
import com.slotlock.booking.entity.Resource;
import com.slotlock.booking.entity.Slot;
import com.slotlock.booking.enums.BookingStatus;
import com.slotlock.booking.enums.SlotStatus;
import com.slotlock.booking.mapper.BookingMapper;
import com.slotlock.booking.repository.BookingRepository;
import com.slotlock.booking.repository.ResourceRepository;
import com.slotlock.booking.repository.SlotRepository;
import com.slotlock.booking.service.BookingService;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class DefaultBookingService implements BookingService {

    private final BookingRepository bookingRepository;
    private final SlotRepository slotRepository;
    private final ResourceRepository resourceRepository;
    private final BookingMapper bookingMapper;
    private final OutboxService outboxService;

    public DefaultBookingService(BookingRepository bookingRepository,
                                  SlotRepository slotRepository,
                                  ResourceRepository resourceRepository,
                                  BookingMapper bookingMapper,
                                  OutboxService outboxService) {
        this.bookingRepository = bookingRepository;
        this.slotRepository = slotRepository;
        this.resourceRepository = resourceRepository;
        this.bookingMapper = bookingMapper;
        this.outboxService = outboxService;
    }

    @Override
    @Transactional
    public BookingResult book(BookingRequest request) {
        // STRICT IDEMPOTENCY, BY DESIGN: an idempotencyKey identifies one specific booking
        // attempt, permanently — not "the current state of this slot for this client". If a key
        // already has a row, we return that row exactly as it is now, including CANCELLED, and
        // never re-run the booking logic for it. This matches how Stripe and most payment APIs
        // treat idempotency keys: reusing a key never produces a second real attempt, regardless
        // of what happened to the original resource afterward (e.g. it was since cancelled).
        // Deliberately NOT "smart" about cancelled bookings releasing their key for reuse — that
        // would mean the same key could yield different outcomes on different calls, which is
        // the exact property idempotency keys exist to prevent. A client that wants to book again
        // after cancelling must send a NEW idempotencyKey — that's the client's responsibility,
        // not something this method infers on their behalf.
        var existing = bookingRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            return new BookingResult(bookingMapper.toResponse(existing.get()), false);
        }

        // PESSIMISTIC LOCKING FIX (was: slotRepository.findById(...), a plain unlocked read).
        // This SELECT now runs as "SELECT ... FOR UPDATE" (see SlotRepository.findByIdForUpdate)
        // and takes an exclusive row lock on this Slot row for the rest of this transaction.
        // Concretely, for two threads A and B racing to book the same slot:
        //   - Thread A's findByIdForUpdate runs first (whichever gets there first — order is
        //     still nondeterministic, that's fine, exactly one has to win): it acquires the
        //     row lock and reads status = OPEN.
        //   - Thread B's findByIdForUpdate arrives while A's transaction is still open. B does
        //     NOT get back a row to read from — B's query physically blocks inside MySQL/InnoDB
        //     and does not return at all until A's transaction ends.
        //   - Thread A proceeds, sets status = BOOKED, saves, and its @Transactional method
        //     returns — committing the transaction and releasing the row lock.
        //   - ONLY NOW does Thread B's blocked SELECT ... FOR UPDATE unblock and return. It
        //     does not see the OPEN value that was true when B's request arrived; it sees
        //     status = BOOKED, because FOR UPDATE reads the true, current, post-commit state of
        //     the row (not a snapshot taken before it started waiting). So B's naive-looking
        //     status check below runs against reality, not a stale read, and correctly throws.
        // This is what actually closes the race: the naive version's bug was never really "the
        // if-check is wrong" — the if-check is fine — it's that the SELECT feeding it could
        // return a value that was already out of date by the time the code acted on it. Locking
        // the SELECT removes that gap entirely, and as a side effect also eliminates the MySQL
        // deadlocks (1213) we saw in the naive run: instead of 50 transactions all racing to
        // UPDATE the same row with no coordination, they now queue up one at a time on the
        // SELECT, so there's no pile of simultaneous lock requests for InnoDB's deadlock
        // detector to find a cycle in.
        Slot slot = slotRepository.findByIdForUpdate(request.slotId())
                .orElseThrow(() -> new BusinessLogicViolationException(
                        HttpStatus.NOT_FOUND, ApiErrorCodeEnum.RESOURCE_NOT_FOUND, "Slot not found"));

        Resource resource = resourceRepository.findById(slot.getResourceId())
                .orElseThrow(() -> new BusinessLogicViolationException(
                        HttpStatus.NOT_FOUND, ApiErrorCodeEnum.RESOURCE_NOT_FOUND, "Slot not found"));

        // Customers are deliberately tenant-less (see resolveBrowseTenantId in
        // DefaultResourceService) so they can book any tenant's resources across every category.
        // ADMIN/STAFF book on behalf of their own tenant only, so they stay locked to it.
        if (isTenantScopedCaller() && !resource.getTenantId().equals(SecurityUtils.getCurrentTenantId())) {
            throw new BusinessLogicViolationException(
                    HttpStatus.NOT_FOUND, ApiErrorCodeEnum.RESOURCE_NOT_FOUND, "Slot not found");
        }

        // No longer a naive check: by the time we get here, findByIdForUpdate has already
        // guaranteed this read reflects the true current state of the row (see comment above).
        if (slot.getStatus() != SlotStatus.OPEN) {
            throw new SlotConflictException("Slot is no longer available");
        }

        slot.setStatus(SlotStatus.BOOKED);
        slotRepository.save(slot);

        Booking booking = Booking.builder()
                .slotId(slot.getId())
                .customerId(SecurityUtils.getCurrentUserId())
                .status(BookingStatus.CONFIRMED)
                .idempotencyKey(request.idempotencyKey())
                .build();
        Booking saved = bookingRepository.save(booking);

        outboxService.recordEvent("BOOKING", saved.getId(), "outbox.booking_confirmed",
                Map.of("bookingId", saved.getId(), "slotId", slot.getId(), "customerId", saved.getCustomerId()));

        return new BookingResult(bookingMapper.toResponse(saved), true);
    }

    // OPTIMISTIC LOCKING PATH — a separate implementation from book(), not a variant of it, so
    // the two strategies stay independently testable and comparable. Do not merge these.
    //
    // The mechanism here is NOT something added by this method — Slot.version (@Version) has
    // been present since before either locking strategy existed, so Hibernate has ALWAYS been
    // appending "AND version = ?" to every UPDATE it generates for a Slot, and has always thrown
    // when that predicate matches 0 rows. That was true even in the fully naive version. What's
    // actually new here is (a) reading the slot with a plain, unlocked findById — deliberately
    // no row lock, unlike book()'s findByIdForUpdate — and (b) catching the exception that
    // version check produces and translating it into the same clean SlotConflictException the
    // pessimistic path throws, instead of letting a low-level persistence exception leak out as
    // an unhandled 500.
    @Override
    @Transactional
    public BookingResult bookOptimistic(BookingRequest request) {
        // STRICT IDEMPOTENCY, BY DESIGN — same rule as book(), see the comment there. A key
        // whose booking was since cancelled still short-circuits here and returns that cancelled
        // row as-is. Re-booking after a cancellation requires a new idempotencyKey from the
        // client; this method will never infer that intent from a reused key.
        var existing = bookingRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            return new BookingResult(bookingMapper.toResponse(existing.get()), false);
        }

        // Plain, unlocked read — deliberately the opposite of book()'s findByIdForUpdate. No row
        // lock is taken here, so multiple threads can (and will, under concurrency) all read the
        // same OPEN row at once. This method does not try to prevent that; it relies entirely on
        // the version check below to catch it at write time instead of at read time.
        Slot slot = slotRepository.findById(request.slotId())
                .orElseThrow(() -> new BusinessLogicViolationException(
                        HttpStatus.NOT_FOUND, ApiErrorCodeEnum.RESOURCE_NOT_FOUND, "Slot not found"));

        Resource resource = resourceRepository.findById(slot.getResourceId())
                .orElseThrow(() -> new BusinessLogicViolationException(
                        HttpStatus.NOT_FOUND, ApiErrorCodeEnum.RESOURCE_NOT_FOUND, "Slot not found"));

        // Customers are deliberately tenant-less (see resolveBrowseTenantId in
        // DefaultResourceService) so they can book any tenant's resources across every category.
        // ADMIN/STAFF book on behalf of their own tenant only, so they stay locked to it.
        if (isTenantScopedCaller() && !resource.getTenantId().equals(SecurityUtils.getCurrentTenantId())) {
            throw new BusinessLogicViolationException(
                    HttpStatus.NOT_FOUND, ApiErrorCodeEnum.RESOURCE_NOT_FOUND, "Slot not found");
        }

        // Same naive-looking check as the original unsafe version — and it genuinely is just as
        // capable of reading a stale OPEN here as the naive version was. This check is not what
        // makes this method safe. What makes it safe is that the UPDATE below is not trusted to
        // succeed just because this check passed.
        if (slot.getStatus() != SlotStatus.OPEN) {
            throw new SlotConflictException("Slot is no longer available");
        }

        slot.setStatus(SlotStatus.BOOKED);

        try {
            // saveAndFlush, not save. save() may leave the actual UPDATE statement queued in
            // Hibernate's session and only send it to the DB when the transaction commits — which
            // happens outside this method, after control has already returned to the caller. If
            // the version conflict fired at that point, there would be no try-catch left to catch
            // it; it would surface as an opaque failure from the transaction manager, not from
            // this method. saveAndFlush forces the UPDATE (and therefore the
            // "AND version = ?" check) to execute synchronously, right here, so any conflict
            // happens inside this try block where we can actually handle it.
            slotRepository.saveAndFlush(slot);
        } catch (ObjectOptimisticLockingFailureException e) {
            // This is the exception Spring Data JPA actually surfaces (it wraps the lower-level
            // JPA OptimisticLockException) when the UPDATE's "AND version = ?" predicate matched
            // 0 rows — i.e. some other transaction already committed a change to this row since
            // we read it, so the version we read is stale. That means the slot is already booked;
            // there is nothing to retry, the outcome is final, so we translate straight to the
            // same conflict response the pessimistic path gives its losers.
            throw new SlotConflictException("Slot is no longer available");
        }
        // Deliberately NOT catching org.springframework.dao.CannotAcquireLockException
        // (deadlocks) here. Whether concurrent blind UPDATEs to the same row still produce InnoDB
        // deadlocks under this strategy is exactly the open question the concurrency test is
        // measuring — silently swallowing that evidence here would defeat the point of measuring
        // it.

        Booking booking = Booking.builder()
                .slotId(slot.getId())
                .customerId(SecurityUtils.getCurrentUserId())
                .status(BookingStatus.CONFIRMED)
                .idempotencyKey(request.idempotencyKey())
                .build();
        Booking saved = bookingRepository.save(booking);

        outboxService.recordEvent("BOOKING", saved.getId(), "outbox.booking_confirmed",
                Map.of("bookingId", saved.getId(), "slotId", slot.getId(), "customerId", saved.getCustomerId()));

        return new BookingResult(bookingMapper.toResponse(saved), true);
    }

    @Override
    @Transactional
    public void cancel(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BusinessLogicViolationException(
                        HttpStatus.NOT_FOUND, ApiErrorCodeEnum.RESOURCE_NOT_FOUND, "Booking not found"));

        if (!isOwnerOrAdmin(booking)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN, ApiErrorCodeEnum.ACCESS_DENIED, "Not authorized to cancel this booking");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        Slot slot = slotRepository.findById(booking.getSlotId())
                .orElseThrow(() -> new BusinessLogicViolationException(
                        HttpStatus.NOT_FOUND, ApiErrorCodeEnum.RESOURCE_NOT_FOUND, "Slot not found"));
        slot.setStatus(SlotStatus.OPEN);
        slotRepository.save(slot);

        outboxService.recordEvent("BOOKING", booking.getId(), "outbox.booking_cancelled",
                Map.of("bookingId", booking.getId(), "slotId", slot.getId(), "customerId", booking.getCustomerId()));

        // Exact routing key, no "outbox." prefix — this is what RabbitMQConfig binds the
        // waitlist promotion queue to (see waitlistPromotionBinding), distinct from the
        // "outbox.#" wildcard the notification consumer listens on above.
        outboxService.recordEvent("SLOT", slot.getId(), "slot.opened",
                Map.of("slotId", slot.getId(), "resourceId", slot.getResourceId()));
    }

    @Override
    public BookingResponse getById(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new BusinessLogicViolationException(
                        HttpStatus.NOT_FOUND, ApiErrorCodeEnum.RESOURCE_NOT_FOUND, "Booking not found"));

        if (!isOwnerOrAdmin(booking)) {
            throw new BusinessLogicViolationException(
                    HttpStatus.NOT_FOUND, ApiErrorCodeEnum.RESOURCE_NOT_FOUND, "Booking not found");
        }

        return bookingMapper.toResponse(booking);
    }

    @Override
    public List<BookingResponse> listForCurrentUser() {
        String role = SecurityUtils.getCurrentUserRole();
        List<Booking> bookings = "ADMIN".equals(role) || "STAFF".equals(role)
                ? bookingRepository.findAllByTenantId(SecurityUtils.getCurrentTenantId())
                : bookingRepository.findByCustomerId(SecurityUtils.getCurrentUserId());

        return bookings.stream().map(bookingMapper::toResponse).toList();
    }

    @Override
    @Transactional
    public void delete(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BusinessLogicViolationException(
                        HttpStatus.NOT_FOUND, ApiErrorCodeEnum.RESOURCE_NOT_FOUND, "Booking not found"));

        Slot slot = slotRepository.findById(booking.getSlotId())
                .orElseThrow(() -> new BusinessLogicViolationException(
                        HttpStatus.NOT_FOUND, ApiErrorCodeEnum.RESOURCE_NOT_FOUND, "Booking not found"));

        Resource resource = resourceRepository.findById(slot.getResourceId())
                .orElseThrow(() -> new BusinessLogicViolationException(
                        HttpStatus.NOT_FOUND, ApiErrorCodeEnum.RESOURCE_NOT_FOUND, "Booking not found"));

        if (isTenantScopedCaller() && !resource.getTenantId().equals(SecurityUtils.getCurrentTenantId())) {
            throw new BusinessLogicViolationException(
                    HttpStatus.NOT_FOUND, ApiErrorCodeEnum.RESOURCE_NOT_FOUND, "Booking not found");
        }

        if (booking.getStatus() != BookingStatus.CANCELLED) {
            throw new BusinessLogicViolationException(
                    HttpStatus.CONFLICT, ApiErrorCodeEnum.BUSINESS_RULE_VIOLATION,
                    "Only cancelled bookings can be deleted");
        }

        bookingRepository.delete(booking);
    }

    private boolean isTenantScopedCaller() {
        String role = SecurityUtils.getCurrentUserRole();
        return "ADMIN".equals(role) || "STAFF".equals(role);
    }

    private boolean isOwnerOrAdmin(Booking booking) {
        if (SecurityUtils.getCurrentUserId().equals(booking.getCustomerId())) {
            return true;
        }
        String role = SecurityUtils.getCurrentUserRole();
        return "ADMIN".equals(role) || "SUPER_ADMIN".equals(role);
    }
}

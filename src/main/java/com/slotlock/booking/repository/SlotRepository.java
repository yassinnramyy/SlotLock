package com.slotlock.booking.repository;

import com.slotlock.booking.entity.Slot;
import com.slotlock.booking.enums.SlotStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SlotRepository extends JpaRepository<Slot, Long> {

    List<Slot> findByResourceIdAndStartAtBetween(Long resourceId, LocalDateTime from, LocalDateTime to);

    boolean existsByResourceIdAndStartAt(Long resourceId, LocalDateTime startAt);

    boolean existsByResourceIdAndStatus(Long resourceId, SlotStatus status);

    void deleteByResourceId(Long resourceId);

    // PESSIMISTIC_WRITE tells Hibernate to append "FOR UPDATE" to the SQL this query runs, so
    // the generated statement is literally:
    //     SELECT s.id, s.resource_id, s.start_at, s.end_at, s.status, s.version
    //     FROM slots s WHERE s.id = ? FOR UPDATE
    // "FOR UPDATE" makes InnoDB take an exclusive row lock as part of the SELECT itself — the
    // read and the lock happen atomically, in one round trip, with no gap between them for
    // another transaction to slip in. Any other transaction that also tries to
    // SELECT ... FOR UPDATE (or UPDATE/DELETE) the same row simply blocks at the database level
    // until this transaction commits or rolls back; it does not get back a stale row it can act
    // on, it does not return at all until it's safe to. That's the core mechanism that closes
    // the naive version's race: the naive code's plain SELECT never blocked anyone, so 50
    // threads could all read status = OPEN before any of them wrote BOOKED back.
    //
    // Why an explicit @Query instead of just putting @Lock on a derived/inherited findById:
    // JpaRepository already provides findById(Long) with no lock mode, and Spring Data's
    // @Lock annotation is applied per declared repository METHOD, not per call site — you
    // cannot selectively re-annotate the one inherited findById() you didn't declare yourself
    // in this interface, and attempts to override it with a different signature/lock mode are
    // fragile and inconsistent across Spring Data versions. Declaring our own JPQL query here
    // keeps the query text and the lock mode explicitly paired on one method, so there's no
    // ambiguity about which reads are locked (this one) and which aren't (every other read in
    // this repository, e.g. the ones used by slot generation/listing, which have no business
    // taking a write lock).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Slot s WHERE s.id = :id")
    Optional<Slot> findByIdForUpdate(@Param("id") Long id);
}

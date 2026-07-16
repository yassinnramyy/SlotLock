package com.slotlock.booking.repository;

import com.slotlock.booking.entity.Booking;
import com.slotlock.booking.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByIdempotencyKey(String idempotencyKey);

    Optional<Booking> findBySlotIdAndStatus(Long slotId, BookingStatus status);

    List<Booking> findByCustomerId(Long customerId);

    @Query("""
            SELECT b FROM Booking b
            JOIN Slot s ON b.slotId = s.id
            JOIN Resource r ON s.resourceId = r.id
            WHERE r.tenantId = :tenantId
            """)
    List<Booking> findAllByTenantId(@Param("tenantId") Long tenantId);
}

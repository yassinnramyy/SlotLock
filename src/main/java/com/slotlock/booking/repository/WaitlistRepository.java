package com.slotlock.booking.repository;

import com.slotlock.booking.entity.WaitlistEntry;
import com.slotlock.booking.enums.WaitlistStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WaitlistRepository extends JpaRepository<WaitlistEntry, Long> {

    List<WaitlistEntry> findByCustomerId(Long customerId);

    Optional<WaitlistEntry> findFirstByResourceIdAndStatusOrderByCreatedAtAsc(Long resourceId, WaitlistStatus status);
}

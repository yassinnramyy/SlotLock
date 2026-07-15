package com.slotlock.booking.repository;

import com.slotlock.booking.entity.Slot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface SlotRepository extends JpaRepository<Slot, Long> {

    List<Slot> findByResourceIdAndStartAtBetween(Long resourceId, LocalDateTime from, LocalDateTime to);

    boolean existsByResourceIdAndStartAt(Long resourceId, LocalDateTime startAt);
}

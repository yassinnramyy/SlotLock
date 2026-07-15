package com.slotlock.booking.repository;

import com.slotlock.booking.entity.AvailabilityWindow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AvailabilityWindowRepository extends JpaRepository<AvailabilityWindow, Long> {

    List<AvailabilityWindow> findByResourceId(Long resourceId);

    List<AvailabilityWindow> findByResourceIdAndDayOfWeek(Long resourceId, Integer dayOfWeek);

    void deleteByResourceId(Long resourceId);
}

package com.slotlock.booking.dto.response;

import java.time.LocalTime;

public record AvailabilityWindowResponse(
        Long id,
        Integer dayOfWeek,
        LocalTime startTime,
        LocalTime endTime
) {
}

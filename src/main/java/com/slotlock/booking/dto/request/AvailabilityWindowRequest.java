package com.slotlock.booking.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record AvailabilityWindowRequest(
        @NotNull @Min(0) @Max(6) Integer dayOfWeek,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime
) {
}

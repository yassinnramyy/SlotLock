package com.slotlock.booking.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ResourceRequest(
        @NotBlank String name,
        String description,
        Integer slotDurationMinutes,
        @NotEmpty @Valid List<AvailabilityWindowRequest> availabilityWindows
) {
}

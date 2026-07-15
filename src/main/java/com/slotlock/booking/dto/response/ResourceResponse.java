package com.slotlock.booking.dto.response;

import java.util.List;

public record ResourceResponse(
        Long id,
        Long tenantId,
        String name,
        String description,
        Integer slotDurationMinutes,
        List<AvailabilityWindowResponse> availabilityWindows
) {
}

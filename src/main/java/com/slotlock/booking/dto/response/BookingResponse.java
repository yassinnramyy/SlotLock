package com.slotlock.booking.dto.response;

import java.time.Instant;

public record BookingResponse(
        Long id,
        Long slotId,
        Long customerId,
        String status,
        Instant createdAt
) {
}

package com.slotlock.booking.dto.response;

import java.time.Instant;
import java.time.LocalDateTime;

public record WaitlistEntryResponse(
        Long id,
        Long resourceId,
        LocalDateTime requestedStartAt,
        LocalDateTime requestedEndAt,
        Long customerId,
        String status,
        Instant createdAt
) {
}

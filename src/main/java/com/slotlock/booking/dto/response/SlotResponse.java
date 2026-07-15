package com.slotlock.booking.dto.response;

import java.time.LocalDateTime;

public record SlotResponse(
        Long id,
        Long resourceId,
        LocalDateTime startAt,
        LocalDateTime endAt,
        String status
) {
}

package com.slotlock.booking.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record WaitlistRequest(
        @NotNull Long resourceId,
        @NotNull LocalDateTime requestedStartAt,
        @NotNull LocalDateTime requestedEndAt
) {
}

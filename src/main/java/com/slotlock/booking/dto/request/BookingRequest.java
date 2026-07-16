package com.slotlock.booking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BookingRequest(
        @NotNull Long slotId,
        @NotBlank String idempotencyKey
) {
}

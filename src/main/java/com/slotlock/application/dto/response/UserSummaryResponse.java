package com.slotlock.application.dto.response;

import java.time.Instant;

public record UserSummaryResponse(
        Long userId,
        String email,
        Long tenantId,
        String role,
        Instant createdAt
) {
}

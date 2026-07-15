package com.slotlock.masterdata.dto.response;

import java.time.Instant;

public record TenantResponse(
        Long id,
        String name,
        String slug,
        Instant createdAt
) {
}
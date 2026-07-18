package com.slotlock.masterdata.dto.response;

import com.slotlock.masterdata.enums.TenantCategoryEnum;

import java.time.Instant;

public record TenantResponse(
        Long id,
        String name,
        String slug,
        TenantCategoryEnum category,
        Instant createdAt
) {
}
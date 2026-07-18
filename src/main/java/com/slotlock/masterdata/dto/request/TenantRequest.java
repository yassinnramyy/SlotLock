package com.slotlock.masterdata.dto.request;

import com.slotlock.masterdata.enums.TenantCategoryEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record TenantRequest(
        @NotBlank String name,
        @NotBlank
        @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "slug must be lowercase letters, numbers, and hyphens only")
        String slug,
        @NotNull TenantCategoryEnum category
) {
}
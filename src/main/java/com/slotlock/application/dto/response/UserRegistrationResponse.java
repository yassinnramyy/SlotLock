package com.slotlock.application.dto.response;

public record UserRegistrationResponse(
        Long userId,
        Long tenantId,
        String role,
        String token
) {
}

package com.slotlock.application.dto.response;

public record UserLoginResponse(
        Long userId,
        Long tenantId,
        String role,
        String token
) {
}

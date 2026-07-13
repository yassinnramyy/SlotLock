package com.slotlock.application.dto.request;

import com.slotlock.application.enums.UserRoleEnum;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// TODO: letting callers self-assign role at registration is fine for local dev/testing
// but must be locked down (e.g. admin-only user creation endpoint) before this is ever exposed publicly.
public record UserRegistrationRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String password,
        Long tenantId,
        UserRoleEnum role
) {
}

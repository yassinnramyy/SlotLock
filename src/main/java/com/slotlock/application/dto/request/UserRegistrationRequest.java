package com.slotlock.application.dto.request;

import com.slotlock.application.enums.UserRoleEnum;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Public self-registration. role is only ever null/CUSTOMER, or a one-time SUPER_ADMIN
// bootstrap that AuthenticationService rejects once a SUPER_ADMIN already exists.
// ADMIN and STAFF accounts can only be created via the dedicated /api/auth/admins
// and /api/auth/staff endpoints.
public record UserRegistrationRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String password,
        UserRoleEnum role
) {
}

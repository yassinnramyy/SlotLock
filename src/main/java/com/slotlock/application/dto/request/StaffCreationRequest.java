package com.slotlock.application.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StaffCreationRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String password
) {
}

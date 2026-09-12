package com.fitnesshub.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The current password is required even though the caller is already
 * authenticated: it stops someone who walks up to an unlocked phone from
 * silently taking over the account.
 */
public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 8, max = 128) String newPassword
) {
}

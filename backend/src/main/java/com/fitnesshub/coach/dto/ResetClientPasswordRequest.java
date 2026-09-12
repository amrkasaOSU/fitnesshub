package com.fitnesshub.coach.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A coach setting a temporary password for a client who is locked out. Not a
 * self-service reset - there is no email delivery, so recovery runs through
 * the coach, who already knows the client personally.
 */
public record ResetClientPasswordRequest(
        @NotBlank @Size(min = 8, max = 128) String temporaryPassword
) {
}

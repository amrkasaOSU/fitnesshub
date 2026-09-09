package com.fitnesshub.user.dto;

import com.fitnesshub.user.Role;

import java.time.Instant;
import java.util.UUID;

public record UserDto(
        UUID id,
        String email,
        String firstName,
        String lastName,
        Role role,
        String profileImageUrl,
        String timezone,
        boolean emailVerified,
        Instant lastLoginAt,
        Instant createdAt
) {
}

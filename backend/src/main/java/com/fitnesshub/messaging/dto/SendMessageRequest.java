package com.fitnesshub.messaging.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record SendMessageRequest(
        /** Required when the sender is a coach (identifies which client's conversation); ignored for a client sender. */
        UUID clientId,
        @NotBlank String content
) {
}

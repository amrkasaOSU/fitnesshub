package com.fitnesshub.messaging.dto;

import java.time.Instant;
import java.util.UUID;

public record ConversationDto(
        UUID id,
        UUID coachId,
        UUID clientId,
        String otherPartyName,
        String lastMessagePreview,
        Instant lastMessageAt,
        long unreadCount
) {
}

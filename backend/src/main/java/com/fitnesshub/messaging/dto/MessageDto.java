package com.fitnesshub.messaging.dto;

import java.time.Instant;
import java.util.UUID;

public record MessageDto(UUID id, UUID conversationId, UUID senderId, String content, Instant createdAt, Instant readAt) {
}

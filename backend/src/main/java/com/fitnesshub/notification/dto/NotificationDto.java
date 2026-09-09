package com.fitnesshub.notification.dto;

import com.fitnesshub.notification.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationDto(
        UUID id,
        NotificationType type,
        String title,
        String body,
        String relatedType,
        UUID relatedId,
        Instant createdAt,
        Instant readAt
) {
}

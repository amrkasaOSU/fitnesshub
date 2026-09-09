package com.fitnesshub.coach.dto;

import java.time.Instant;
import java.util.UUID;

public record CoachNoteDto(UUID id, String content, Instant createdAt) {
}

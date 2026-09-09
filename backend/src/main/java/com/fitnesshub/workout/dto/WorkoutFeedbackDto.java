package com.fitnesshub.workout.dto;

import java.time.Instant;
import java.util.UUID;

public record WorkoutFeedbackDto(UUID id, String content, Instant createdAt) {
}

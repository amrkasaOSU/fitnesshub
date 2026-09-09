package com.fitnesshub.workout.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WorkoutSetDto(
        UUID id,
        int setNumber,
        int reps,
        BigDecimal weight,
        BigDecimal rpe,
        BigDecimal rir,
        boolean isWarmup,
        boolean isFailure,
        Instant completedAt
) {
}

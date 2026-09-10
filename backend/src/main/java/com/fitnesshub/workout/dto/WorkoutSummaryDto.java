package com.fitnesshub.workout.dto;

import com.fitnesshub.workout.WorkoutSessionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WorkoutSummaryDto(
        UUID id,
        String dayName,
        Instant startedAt,
        Integer durationSeconds,
        BigDecimal totalVolume,
        BigDecimal averageRpe,
        WorkoutSessionStatus status,
        int prCount,
        String skipReason
) {
}

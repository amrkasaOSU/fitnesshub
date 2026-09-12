package com.fitnesshub.workout.dto;

import com.fitnesshub.workout.WorkoutSessionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WorkoutSessionDto(
        UUID id,
        /** Null when no program is assigned - distinct from a prescribed rest day, which has an id and no exercises. */
        UUID programDayId,
        String dayName,
        WorkoutSessionStatus status,
        Instant startedAt,
        Instant completedAt,
        Integer durationSeconds,
        String notes,
        BigDecimal overallRpe,
        Integer energyLevel,
        BigDecimal totalVolume,
        int prCount,
        List<WorkoutExerciseDto> exercises,
        List<WorkoutFeedbackDto> feedback,
        String skipReason
) {
}

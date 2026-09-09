package com.fitnesshub.analytics.dto;

import java.math.BigDecimal;

public record TrainingSummaryDto(
        int workoutsCompleted,
        int workoutsPlanned,
        BigDecimal adherencePercentage,
        int totalSets,
        int totalReps,
        BigDecimal totalVolume,
        BigDecimal averageWorkoutDurationMinutes,
        BigDecimal averageRpe,
        BigDecimal averageRir,
        int prCount
) {
}

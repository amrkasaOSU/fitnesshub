package com.fitnesshub.progress.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ExerciseProgressSummary(
        BigDecimal bestWeight,
        Integer bestReps,
        BigDecimal bestEstimated1Rm,
        BigDecimal latestVolume,
        BigDecimal previousVolume,
        String volumeTrend,
        String performanceTrend,
        BigDecimal recentAverageRpe,
        BigDecimal recentFailureRate,
        List<ExerciseSessionPoint> recentSessions
) {
    public record ExerciseSessionPoint(
            Instant date,
            BigDecimal topWeight,
            Integer topWeightReps,
            BigDecimal estimated1Rm,
            BigDecimal volume,
            BigDecimal averageRpe
    ) {
    }
}

package com.fitnesshub.workout.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** The client's last completed working set(s) for an exercise, shown next to today's target. */
public record PreviousPerformanceDto(
        Instant date,
        BigDecimal weight,
        int reps,
        BigDecimal rpe
) {
}

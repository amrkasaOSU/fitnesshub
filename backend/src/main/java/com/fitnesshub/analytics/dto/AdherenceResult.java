package com.fitnesshub.analytics.dto;

import java.math.BigDecimal;

/** {@code plannedWorkouts == 0} means there's no active program to measure against (never penalize for that). */
public record AdherenceResult(int completedWorkouts, int plannedWorkouts, BigDecimal adherencePercentage) {

    public static AdherenceResult noActiveProgram(int completedWorkouts) {
        return new AdherenceResult(completedWorkouts, 0, null);
    }
}

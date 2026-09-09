package com.fitnesshub.steps.dto;

import java.math.BigDecimal;

public record StepDashboardDto(
        int todaySteps,
        BigDecimal weeklyAverage,
        BigDecimal sevenDayAverage,
        long weeklyTotal,
        int goal,
        BigDecimal goalCompletionPercentage
) {
}

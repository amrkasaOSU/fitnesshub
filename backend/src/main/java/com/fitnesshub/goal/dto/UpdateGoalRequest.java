package com.fitnesshub.goal.dto;

import com.fitnesshub.goal.GoalStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateGoalRequest(
        String name,
        BigDecimal targetValue,
        BigDecimal currentValue,
        LocalDate targetDate,
        GoalStatus status
) {
}

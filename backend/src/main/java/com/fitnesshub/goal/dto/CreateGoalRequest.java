package com.fitnesshub.goal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateGoalRequest(
        @NotNull com.fitnesshub.goal.GoalType type,
        @NotBlank String name,
        @NotNull BigDecimal targetValue,
        BigDecimal currentValue,
        @NotBlank String unit,
        LocalDate targetDate
) {
}

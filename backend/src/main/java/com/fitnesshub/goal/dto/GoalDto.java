package com.fitnesshub.goal.dto;

import com.fitnesshub.goal.GoalStatus;
import com.fitnesshub.goal.GoalType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record GoalDto(
        UUID id,
        GoalType type,
        String name,
        BigDecimal targetValue,
        BigDecimal currentValue,
        String unit,
        LocalDate targetDate,
        GoalStatus status,
        BigDecimal percentComplete
) {
}

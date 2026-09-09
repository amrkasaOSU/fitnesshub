package com.fitnesshub.workout.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

public record CompleteWorkoutRequest(
        String notes,
        @DecimalMin("0") @DecimalMax("10") BigDecimal overallRpe,
        @Min(1) @Max(5) Integer energyLevel
) {
}

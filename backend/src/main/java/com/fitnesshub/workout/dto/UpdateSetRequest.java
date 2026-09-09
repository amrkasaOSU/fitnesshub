package com.fitnesshub.workout.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record UpdateSetRequest(
        @Positive Integer reps,
        @PositiveOrZero BigDecimal weight,
        @DecimalMin("0") @DecimalMax("10") BigDecimal rpe,
        @DecimalMin("0") @DecimalMax("10") BigDecimal rir,
        Boolean isFailure
) {
}

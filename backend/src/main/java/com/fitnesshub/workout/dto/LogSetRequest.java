package com.fitnesshub.workout.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

public record LogSetRequest(
        @NotNull UUID exerciseId,
        @NotNull @Positive Integer reps,
        @NotNull @PositiveOrZero BigDecimal weight,
        @DecimalMin("0") @DecimalMax("10") BigDecimal rpe,
        @DecimalMin("0") @DecimalMax("10") BigDecimal rir,
        boolean isWarmup,
        boolean isFailure
) {
}

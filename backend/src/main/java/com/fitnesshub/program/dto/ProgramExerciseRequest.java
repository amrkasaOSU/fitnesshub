package com.fitnesshub.program.dto;

import com.fitnesshub.program.ProgressionStrategy;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

public record ProgramExerciseRequest(
        @NotNull UUID exerciseId,
        @NotNull @Min(0) Integer orderIndex,
        @NotNull @Min(1) Integer sets,
        @NotNull @Min(1) Integer targetReps,
        @PositiveOrZero BigDecimal targetWeight,
        @DecimalMin("0") @DecimalMax("10") BigDecimal targetRpe,
        @PositiveOrZero Integer restSeconds,
        String tempo,
        String notes,
        ProgressionStrategy progressionStrategy
) {
}

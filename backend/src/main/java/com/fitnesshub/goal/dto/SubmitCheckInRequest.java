package com.fitnesshub.goal.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SubmitCheckInRequest(
        @NotNull LocalDate weekStartDate,
        @Positive BigDecimal weight,
        @NotNull @Min(1) @Max(5) Integer energyScore,
        @NotNull @Min(1) @Max(5) Integer sleepScore,
        @NotNull @Min(1) @Max(5) Integer stressScore,
        @NotNull @Min(1) @Max(5) Integer hungerScore,
        @NotNull @Min(1) @Max(5) Integer workoutAdherence,
        @NotNull @Min(1) @Max(5) Integer nutritionAdherence,
        String notes
) {
}

package com.fitnesshub.nutrition.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LogNutritionRequest(
        LocalDate date,
        @NotNull @PositiveOrZero BigDecimal calories,
        @NotNull @PositiveOrZero BigDecimal proteinGrams,
        @PositiveOrZero BigDecimal carbohydratesGrams,
        @PositiveOrZero BigDecimal fatGrams,
        @PositiveOrZero BigDecimal fiberGrams,
        @PositiveOrZero Integer waterMl,
        String notes
) {
}

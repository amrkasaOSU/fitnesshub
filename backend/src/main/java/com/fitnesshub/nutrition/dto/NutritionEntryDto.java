package com.fitnesshub.nutrition.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record NutritionEntryDto(
        UUID id,
        LocalDate date,
        BigDecimal calories,
        BigDecimal proteinGrams,
        BigDecimal carbohydratesGrams,
        BigDecimal fatGrams,
        BigDecimal fiberGrams,
        Integer waterMl,
        String notes
) {
}

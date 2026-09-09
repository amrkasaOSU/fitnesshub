package com.fitnesshub.nutrition.dto;

import java.math.BigDecimal;

/** {@code available=false} means we don't have enough profile data (height/weight/age/sex) to estimate yet. */
public record CalorieEstimate(
        boolean available,
        BigDecimal estimatedActiveCalories,
        BigDecimal estimatedTotalCalories,
        String note
) {
    public static CalorieEstimate unavailable(String note) {
        return new CalorieEstimate(false, null, null, note);
    }
}

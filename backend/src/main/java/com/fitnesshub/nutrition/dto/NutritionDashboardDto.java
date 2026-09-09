package com.fitnesshub.nutrition.dto;

import java.math.BigDecimal;

public record NutritionDashboardDto(
        BigDecimal caloriesConsumedToday,
        BigDecimal calorieTarget,
        BigDecimal caloriesRemaining,
        BigDecimal proteinConsumedToday,
        BigDecimal proteinTarget,
        BigDecimal proteinRemaining,
        BigDecimal sevenDayAverageCalories,
        BigDecimal sevenDayAverageProtein,
        CalorieEstimate estimatedCaloriesBurned,
        BigDecimal estimatedCalorieBalance
) {
}

package com.fitnesshub.bodyweight.dto;

import java.math.BigDecimal;

public record WeightDashboardDto(
        BigDecimal current,
        BigDecimal starting,
        BigDecimal lowest,
        BigDecimal highest,
        BigDecimal weeklyAverage,
        String weeklyAverageMessage,
        BigDecimal weeklyChange,
        BigDecimal monthlyChange,
        BigDecimal totalChange,
        BigDecimal goalWeight
) {
}

package com.fitnesshub.client.dto;

import java.math.BigDecimal;

public record UpdateClientProfileRequest(
        BigDecimal targetWeight,
        BigDecimal dailyCalorieTarget,
        BigDecimal dailyProteinTarget,
        Integer dailyStepTarget
) {
}

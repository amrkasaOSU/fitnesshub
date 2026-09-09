package com.fitnesshub.bodyweight.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

public record LogWeightRequest(
        @NotNull @Positive @DecimalMax("1500") BigDecimal weight,
        Instant recordedAt,
        String notes
) {
}

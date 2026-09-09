package com.fitnesshub.bodyweight.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record WeightTrendPointDto(Instant recordedAt, BigDecimal weight, BigDecimal sevenDayAverage) {
}

package com.fitnesshub.bodyweight.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WeightEntryDto(UUID id, BigDecimal weight, Instant recordedAt, String notes) {
}

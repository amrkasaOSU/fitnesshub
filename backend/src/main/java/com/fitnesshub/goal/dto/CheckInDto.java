package com.fitnesshub.goal.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CheckInDto(
        UUID id,
        UUID clientId,
        LocalDate weekStartDate,
        BigDecimal weight,
        Integer energyScore,
        Integer sleepScore,
        Integer stressScore,
        Integer hungerScore,
        Integer workoutAdherence,
        Integer nutritionAdherence,
        String notes,
        Instant submittedAt,
        Instant coachReviewedAt,
        String coachResponse,
        boolean needsReview
) {
}

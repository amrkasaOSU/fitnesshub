package com.fitnesshub.coach.dto;

import com.fitnesshub.client.FitnessGoal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ClientSummaryDto(
        UUID clientId,
        String firstName,
        String lastName,
        String email,
        FitnessGoal goal,
        BigDecimal currentWeight,
        BigDecimal weeklyWeightChange,
        BigDecimal adherencePercentage,
        Instant lastWorkoutAt,
        int todaySteps,
        BigDecimal caloriesToday,
        List<String> attentionFlags
) {
}

package com.fitnesshub.client.dto;

import com.fitnesshub.client.ActivityLevel;
import com.fitnesshub.client.FitnessGoal;
import com.fitnesshub.client.Sex;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ClientDetailDto(
        UUID userId,
        String firstName,
        String lastName,
        String email,
        UUID coachId,
        LocalDate dateOfBirth,
        BigDecimal heightCm,
        Sex sex,
        FitnessGoal fitnessGoal,
        ActivityLevel activityLevel,
        BigDecimal startingWeight,
        BigDecimal targetWeight,
        BigDecimal dailyCalorieTarget,
        BigDecimal dailyProteinTarget,
        Integer dailyStepTarget,
        String unitSystem
) {
}

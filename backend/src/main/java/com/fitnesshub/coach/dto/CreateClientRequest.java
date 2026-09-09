package com.fitnesshub.coach.dto;

import com.fitnesshub.client.ActivityLevel;
import com.fitnesshub.client.FitnessGoal;
import com.fitnesshub.client.Sex;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateClientRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 128) String temporaryPassword,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotNull FitnessGoal fitnessGoal,
        ActivityLevel activityLevel,
        LocalDate dateOfBirth,
        BigDecimal heightCm,
        Sex sex,
        BigDecimal startingWeight,
        BigDecimal targetWeight,
        BigDecimal dailyCalorieTarget,
        BigDecimal dailyProteinTarget,
        Integer dailyStepTarget
) {
}

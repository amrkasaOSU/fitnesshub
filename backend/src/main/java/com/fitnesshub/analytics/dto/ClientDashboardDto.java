package com.fitnesshub.analytics.dto;

import com.fitnesshub.bodyweight.dto.WeightDashboardDto;
import com.fitnesshub.goal.dto.GoalDto;
import com.fitnesshub.nutrition.dto.NutritionDashboardDto;
import com.fitnesshub.progress.dto.PersonalRecordDto;
import com.fitnesshub.steps.dto.StepDashboardDto;
import com.fitnesshub.workout.dto.WorkoutSessionDto;

import java.util.List;

public record ClientDashboardDto(
        WorkoutSessionDto todaysWorkout,
        NutritionDashboardDto nutrition,
        StepDashboardDto steps,
        WeightDashboardDto weight,
        AdherenceResult weeklyAdherence,
        List<PersonalRecordDto> recentPrs,
        List<GoalDto> activeGoals,
        long unreadMessages,
        long unreadNotifications
) {
}

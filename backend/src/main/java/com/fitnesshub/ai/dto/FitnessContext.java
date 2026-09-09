package com.fitnesshub.ai.dto;

import com.fitnesshub.analytics.dto.AdherenceResult;
import com.fitnesshub.bodyweight.dto.WeightDashboardDto;
import com.fitnesshub.goal.dto.GoalDto;
import com.fitnesshub.nutrition.dto.NutritionDashboardDto;
import com.fitnesshub.progress.dto.ExerciseProgressSummary;
import com.fitnesshub.progress.dto.PersonalRecordDto;
import com.fitnesshub.steps.dto.StepDashboardDto;

import java.util.List;

/**
 * Everything the AI provider is allowed to see for one question - assembled
 * server-side from the asking user's own data (or, for a coach, the selected
 * client's data) before the question is ever sent to the model. See
 * docs/ai.md for the full retrieval -> context -> provider pipeline.
 */
public record FitnessContext(
        String clientFirstName,
        String matchedExerciseName,
        ExerciseProgressSummary exerciseProgress,
        List<PersonalRecordDto> recentPersonalRecords,
        AdherenceResult weeklyAdherence,
        WeightDashboardDto weightDashboard,
        NutritionDashboardDto nutritionDashboard,
        StepDashboardDto stepDashboard,
        List<GoalDto> activeGoals,
        int completedWorkoutsLast30Days
) {
}

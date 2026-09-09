package com.fitnesshub.workout.dto;

import com.fitnesshub.program.ProgressionStrategy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record WorkoutExerciseDto(
        UUID workoutExerciseId,
        UUID exerciseId,
        String exerciseName,
        int orderIndex,
        Integer targetSets,
        Integer targetReps,
        BigDecimal targetWeight,
        BigDecimal targetRpe,
        Integer restSeconds,
        ProgressionStrategy progressionStrategy,
        PreviousPerformanceDto previousPerformance,
        List<WorkoutSetDto> completedSets
) {
}

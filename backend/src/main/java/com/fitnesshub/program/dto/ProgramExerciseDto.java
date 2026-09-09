package com.fitnesshub.program.dto;

import com.fitnesshub.program.ProgressionStrategy;

import java.math.BigDecimal;
import java.util.UUID;

public record ProgramExerciseDto(
        UUID id,
        UUID exerciseId,
        String exerciseName,
        int orderIndex,
        int sets,
        int targetReps,
        BigDecimal targetWeight,
        BigDecimal targetRpe,
        Integer restSeconds,
        String tempo,
        String notes,
        ProgressionStrategy progressionStrategy
) {
}

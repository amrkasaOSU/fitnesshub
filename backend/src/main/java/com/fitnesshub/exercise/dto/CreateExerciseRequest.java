package com.fitnesshub.exercise.dto;

import com.fitnesshub.exercise.Equipment;
import com.fitnesshub.exercise.MovementPattern;
import com.fitnesshub.exercise.MuscleGroup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateExerciseRequest(
        @NotBlank String name,
        String description,
        @NotNull MuscleGroup muscleGroup,
        @NotNull Equipment equipment,
        @NotNull MovementPattern movementPattern,
        String instructions,
        String videoUrl,
        String imageUrl
) {
}

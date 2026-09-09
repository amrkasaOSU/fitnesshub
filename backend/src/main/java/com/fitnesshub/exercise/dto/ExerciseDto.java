package com.fitnesshub.exercise.dto;

import com.fitnesshub.exercise.Equipment;
import com.fitnesshub.exercise.MovementPattern;
import com.fitnesshub.exercise.MuscleGroup;

import java.util.UUID;

public record ExerciseDto(
        UUID id,
        String name,
        String description,
        MuscleGroup muscleGroup,
        Equipment equipment,
        MovementPattern movementPattern,
        String instructions,
        String videoUrl,
        String imageUrl,
        boolean isSystemExercise
) {
}

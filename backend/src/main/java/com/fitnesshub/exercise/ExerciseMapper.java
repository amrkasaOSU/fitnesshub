package com.fitnesshub.exercise;

import com.fitnesshub.exercise.dto.ExerciseDto;
import org.springframework.stereotype.Component;

@Component
public class ExerciseMapper {
    public ExerciseDto toDto(Exercise e) {
        return new ExerciseDto(
                e.getId(), e.getName(), e.getDescription(), e.getMuscleGroup(),
                e.getEquipment(), e.getMovementPattern(), e.getInstructions(),
                e.getVideoUrl(), e.getImageUrl(), e.isSystemExercise()
        );
    }
}

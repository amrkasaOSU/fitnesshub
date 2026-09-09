package com.fitnesshub.exercise;

import com.fitnesshub.common.exception.NotFoundException;
import com.fitnesshub.exercise.dto.CreateExerciseRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ExerciseService {

    private final ExerciseRepository exerciseRepository;

    public ExerciseService(ExerciseRepository exerciseRepository) {
        this.exerciseRepository = exerciseRepository;
    }

    @Transactional(readOnly = true)
    public Page<Exercise> search(String query, MuscleGroup muscleGroup, Equipment equipment, Pageable pageable) {
        if (query != null && !query.isBlank()) {
            return exerciseRepository.findByNameContainingIgnoreCase(query.trim(), pageable);
        }
        if (muscleGroup != null) {
            return exerciseRepository.findByMuscleGroup(muscleGroup, pageable);
        }
        if (equipment != null) {
            return exerciseRepository.findByEquipment(equipment, pageable);
        }
        return exerciseRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Exercise getById(UUID id) {
        return exerciseRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Exercise", id));
    }

    @Transactional
    public Exercise create(CreateExerciseRequest request) {
        Exercise exercise = new Exercise(request.name(), request.muscleGroup(), request.equipment(), request.movementPattern());
        exercise.setDescription(request.description());
        exercise.setInstructions(request.instructions());
        exercise.setVideoUrl(request.videoUrl());
        exercise.setImageUrl(request.imageUrl());
        exercise.setSystemExercise(false);
        return exerciseRepository.save(exercise);
    }
}

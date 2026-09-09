package com.fitnesshub.exercise;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ExerciseRepository extends JpaRepository<Exercise, UUID> {

    Page<Exercise> findByNameContainingIgnoreCase(String name, Pageable pageable);

    Page<Exercise> findByMuscleGroup(MuscleGroup muscleGroup, Pageable pageable);

    Page<Exercise> findByEquipment(Equipment equipment, Pageable pageable);
}

package com.fitnesshub.workout;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WorkoutSetRepository extends JpaRepository<WorkoutSet, UUID> {
    List<WorkoutSet> findByWorkoutExerciseIdOrderBySetNumberAsc(UUID workoutExerciseId);

    List<WorkoutSet> findByWorkoutExerciseIdInOrderBySetNumberAsc(List<UUID> workoutExerciseIds);
}

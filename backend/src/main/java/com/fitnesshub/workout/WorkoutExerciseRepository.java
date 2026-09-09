package com.fitnesshub.workout;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkoutExerciseRepository extends JpaRepository<WorkoutExercise, UUID> {
    List<WorkoutExercise> findByWorkoutSessionIdOrderByOrderIndexAsc(UUID workoutSessionId);

    List<WorkoutExercise> findByExerciseIdAndWorkoutSessionIdIn(UUID exerciseId, List<UUID> sessionIds);

    Optional<WorkoutExercise> findByWorkoutSessionIdAndExerciseId(UUID workoutSessionId, UUID exerciseId);
}

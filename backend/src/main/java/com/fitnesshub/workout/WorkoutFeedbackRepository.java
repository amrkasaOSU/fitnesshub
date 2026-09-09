package com.fitnesshub.workout;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WorkoutFeedbackRepository extends JpaRepository<WorkoutFeedback, UUID> {
    List<WorkoutFeedback> findByWorkoutSessionIdOrderByCreatedAtDesc(UUID workoutSessionId);
}

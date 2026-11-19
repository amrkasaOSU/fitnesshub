package com.fitnesshub.workout;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkoutExerciseRepository extends JpaRepository<WorkoutExercise, UUID> {
    List<WorkoutExercise> findByWorkoutSessionIdOrderByOrderIndexAsc(UUID workoutSessionId);

    List<WorkoutExercise> findByWorkoutSessionIdInOrderByOrderIndexAsc(List<UUID> workoutSessionIds);

    /**
     * Each client's three most recent occurrences of every exercise, across
     * their completed sessions - the input to the per-exercise attention flags.
     * A window function does in one statement what used to take a query per
     * session plus a query per exercise. Columns: workout_exercise_id,
     * client_id, exercise_id, recency (1 = most recent).
     */
    @Query(value = """
            select t.id, t.client_id, t.exercise_id, t.recency
            from (
                select we.id, ws.client_id, we.exercise_id,
                       row_number() over (
                           partition by ws.client_id, we.exercise_id
                           order by ws.started_at desc, we.order_index asc) as recency
                from workout_exercises we
                join workout_sessions ws on ws.id = we.workout_session_id
                where ws.client_id in (:clientIds)
                  and ws.status = 'COMPLETED'
            ) t
            where t.recency <= 3
            order by t.client_id, t.exercise_id, t.recency
            """, nativeQuery = true)
    List<Object[]> findLastThreeOccurrencesPerExercise(@Param("clientIds") List<UUID> clientIds);

    List<WorkoutExercise> findByExerciseIdAndWorkoutSessionIdIn(UUID exerciseId, List<UUID> sessionIds);

    Optional<WorkoutExercise> findByWorkoutSessionIdAndExerciseId(UUID workoutSessionId, UUID exerciseId);
}

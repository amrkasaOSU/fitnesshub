package com.fitnesshub.workout;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface WorkoutSetRepository extends JpaRepository<WorkoutSet, UUID> {
    List<WorkoutSet> findByWorkoutExerciseIdOrderBySetNumberAsc(UUID workoutExerciseId);

    List<WorkoutSet> findByWorkoutExerciseIdInOrderBySetNumberAsc(List<UUID> workoutExerciseIds);

    /**
     * For each requested exercise, the best valid set from the most recent
     * completed session that contains one - the "previous" line shown beside
     * today's target. One row per exercise, in one round trip, instead of
     * walking the client's session history exercise by exercise.
     *
     * <p>Written as a LATERAL subquery with LIMIT 1 so that, with the
     * {@code (client_id, started_at DESC)} session index, PostgreSQL walks the
     * client's sessions newest-first and stops at the first match for each
     * exercise. The same query as a single DISTINCT ON scanned and sorted the
     * client's entire history: 5.3 ms vs 0.26 ms for a client with a year of
     * training (RESULTS.md, section 3).
     *
     * <p>"Valid" mirrors {@link WorkoutSet#isValidForOneRepMaxAndPr()}: no
     * warmups, no failed sets, positive weight and reps. "Best" is heaviest,
     * then most reps, then the earliest set. Columns: exercise_id,
     * completed_at, weight, reps, rpe.
     */
    @Query(value = """
            select e.id, best.completed_at, best.weight, best.reps, best.rpe
            from exercises e
            cross join lateral (
                select s.completed_at, s.weight, s.reps, s.rpe
                from workout_sessions ws
                join workout_exercises we on we.workout_session_id = ws.id and we.exercise_id = e.id
                join workout_sets s on s.workout_exercise_id = we.id
                where ws.client_id = :clientId
                  and ws.status = 'COMPLETED'
                  and ws.id <> :excludeSessionId
                  and s.is_warmup = false
                  and s.is_failure = false
                  and s.reps > 0
                  and s.weight > 0
                order by ws.started_at desc, s.weight desc, s.reps desc, s.set_number asc
                limit 1
            ) best
            where e.id in (:exerciseIds)
            """, nativeQuery = true)
    List<Object[]> findPreviousBestSets(@Param("clientId") UUID clientId,
                                        @Param("excludeSessionId") UUID excludeSessionId,
                                        @Param("exerciseIds") List<UUID> exerciseIds);
}

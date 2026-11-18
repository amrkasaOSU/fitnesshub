package com.fitnesshub.analytics;

import com.fitnesshub.bodyweight.WeightEntry;
import com.fitnesshub.client.ClientProfile;
import com.fitnesshub.goal.CheckIn;
import com.fitnesshub.nutrition.NutritionEntry;
import com.fitnesshub.program.ClientProgram;
import com.fitnesshub.steps.StepEntry;
import com.fitnesshub.user.User;
import com.fitnesshub.workout.WorkoutSession;
import com.fitnesshub.workout.WorkoutSet;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/**
 * Everything needed to summarise one client for their coach, already loaded.
 *
 * <p>{@link ClientSnapshotLoader} fills these for a whole roster in a fixed
 * number of queries; the calculations that read them
 * ({@link AttentionFlagService#evaluate}, {@code CoachClientService}) are then
 * pure functions over this record and touch the database zero times. That
 * split is what keeps the coach dashboard at the same query count whether a
 * coach has five clients or five hundred.
 *
 * @param recentWeights          weigh-ins from the last few weeks, oldest first
 * @param latestWeight           the most recent weigh-in ever, or null
 * @param recentSteps            step entries covering at least the last 7 days
 * @param recentNutrition        nutrition entries covering at least the last 7 days
 * @param activeProgram          the active program assignment, or null
 * @param programCycle           one flag per day of the active program's cycle:
 *                               true if that day prescribes any exercise
 * @param recentSessions         sessions (any status) started in the last ~9 days
 * @param lastCompletedWorkoutAt start time of the most recent completed session, or null
 * @param latestCheckIn          the check-in for the latest submitted week, or null
 * @param lastThreeOccurrencesPerExercise for each exercise the client has trained, the sets
 *                               from its three most recent completed sessions, most recent first
 */
public record ClientSnapshot(
        User user,
        ClientProfile profile,
        List<WeightEntry> recentWeights,
        WeightEntry latestWeight,
        List<StepEntry> recentSteps,
        List<NutritionEntry> recentNutrition,
        ClientProgram activeProgram,
        List<Boolean> programCycle,
        List<WorkoutSession> recentSessions,
        Instant lastCompletedWorkoutAt,
        CheckIn latestCheckIn,
        List<List<List<WorkoutSet>>> lastThreeOccurrencesPerExercise
) {
    public ZoneId zone() {
        try {
            return user.getTimezone() == null ? ZoneId.of("UTC") : ZoneId.of(user.getTimezone());
        } catch (Exception e) {
            return ZoneId.of("UTC");
        }
    }
}

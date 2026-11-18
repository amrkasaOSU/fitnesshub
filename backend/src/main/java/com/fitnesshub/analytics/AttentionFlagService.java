package com.fitnesshub.analytics;

import com.fitnesshub.analytics.dto.AdherenceResult;
import com.fitnesshub.bodyweight.WeightEntry;
import com.fitnesshub.goal.CheckIn;
import com.fitnesshub.nutrition.NutritionEntry;
import com.fitnesshub.progress.ProgressionService;
import com.fitnesshub.steps.StepEntry;
import com.fitnesshub.workout.WorkoutSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Deterministic, rule-based coaching signals - not a diagnosis of anything
 * medical. Every threshold here is documented in docs/fitness-calculations.md
 * so a coach can see exactly why a client was flagged.
 *
 * <p>The rules are a pure function of a {@link ClientSnapshot}: they read
 * rows that are already in memory and never query. Loading is
 * {@link ClientSnapshotLoader}'s job, which is what lets a whole roster be
 * flagged in a fixed number of queries.
 */
@Service
public class AttentionFlagService {

    private static final BigDecimal LOW_ADHERENCE_RATIO = new BigDecimal("0.67");
    private static final BigDecimal STALLED_WEIGHT_BAND = new BigDecimal("1.0");
    private static final BigDecimal HIGH_RPE = new BigDecimal("9.0");
    private static final BigDecimal PERFORMANCE_DROP = new BigDecimal("0.10");
    private static final int DEFAULT_STEP_GOAL = 10000;

    private final ClientSnapshotLoader snapshotLoader;
    private final AdherenceService adherenceService;
    private final ProgressionService progressionService;

    public AttentionFlagService(ClientSnapshotLoader snapshotLoader,
                                AdherenceService adherenceService,
                                ProgressionService progressionService) {
        this.snapshotLoader = snapshotLoader;
        this.adherenceService = adherenceService;
        this.progressionService = progressionService;
    }

    /** Flags for one client. Loads that client's snapshot, then applies the same rules as a roster. */
    @Transactional(readOnly = true)
    public Set<AttentionFlag> computeFlags(UUID clientId, ZoneId zone) {
        ClientSnapshot snapshot = snapshotLoader.load(List.of(clientId)).get(clientId);
        if (snapshot == null) {
            return EnumSet.noneOf(AttentionFlag.class);
        }
        return evaluate(snapshot, zone, Instant.now());
    }

    /** The rules themselves. Pure: same snapshot and clock in, same flags out. */
    public Set<AttentionFlag> evaluate(ClientSnapshot snapshot, ZoneId zone, Instant now) {
        Set<AttentionFlag> flags = EnumSet.noneOf(AttentionFlag.class);
        LocalDate today = LocalDate.ofInstant(now, zone);

        // NO_WORKOUT_LOGGED: nothing at all in the last 7 days.
        Instant weekAgo = now.minus(7, ChronoUnit.DAYS);
        boolean anySessionThisWeek = snapshot.recentSessions().stream()
                .anyMatch(s -> !s.getStartedAt().isBefore(weekAgo) && !s.getStartedAt().isAfter(now));
        if (!anySessionThisWeek) {
            flags.add(AttentionFlag.NO_WORKOUT_LOGGED);
        }

        // LOW_ADHERENCE: >=3 planned workouts in the last 7 days and completed/planned < 0.67.
        AdherenceResult adherence = adherenceService.calculateFromSnapshot(snapshot, today.minusDays(6), today, zone);
        if (adherence.plannedWorkouts() >= 3) {
            BigDecimal ratio = BigDecimal.valueOf(adherence.completedWorkouts())
                    .divide(BigDecimal.valueOf(adherence.plannedWorkouts()), 4, RoundingMode.HALF_UP);
            if (ratio.compareTo(LOW_ADHERENCE_RATIO) < 0) {
                flags.add(AttentionFlag.LOW_ADHERENCE);
            }
        }

        // WEIGHT_TREND_STALLED: at least 3 weigh-ins in the last 3 weeks with essentially no net change.
        Instant threeWeeksAgo = now.minus(21, ChronoUnit.DAYS);
        List<WeightEntry> lastThreeWeeks = snapshot.recentWeights().stream()
                .filter(w -> !w.getRecordedAt().isBefore(threeWeeksAgo))
                .toList();
        if (lastThreeWeeks.size() >= 3) {
            BigDecimal first = lastThreeWeeks.get(0).getWeight();
            BigDecimal last = lastThreeWeeks.get(lastThreeWeeks.size() - 1).getWeight();
            if (first.subtract(last).abs().compareTo(STALLED_WEIGHT_BAND) <= 0) {
                flags.add(AttentionFlag.WEIGHT_TREND_STALLED);
            }
        }

        // CALORIE_TRACKING_INCONSISTENT: fewer than 4 of the last 7 days logged.
        LocalDate sevenDayStart = today.minusDays(6);
        long nutritionDaysLogged = snapshot.recentNutrition().stream()
                .map(NutritionEntry::getDate)
                .filter(d -> !d.isBefore(sevenDayStart) && !d.isAfter(today))
                .count();
        if (nutritionDaysLogged < 4) {
            flags.add(AttentionFlag.CALORIE_TRACKING_INCONSISTENT);
        }

        // STEP_TARGET_MISSED: 7-day average steps below 70% of the active goal (needs at least one entry).
        List<StepEntry> stepsLast7 = snapshot.recentSteps().stream()
                .filter(s -> !s.getDate().isBefore(sevenDayStart) && !s.getDate().isAfter(today))
                .toList();
        if (!stepsLast7.isEmpty()) {
            int goal = (snapshot.profile() != null && snapshot.profile().getDailyStepTarget() != null)
                    ? snapshot.profile().getDailyStepTarget() : DEFAULT_STEP_GOAL;
            double avgSteps = stepsLast7.stream().mapToInt(StepEntry::getSteps).average().orElse(0);
            if (avgSteps < goal * 0.7) {
                flags.add(AttentionFlag.STEP_TARGET_MISSED);
            }
        }

        // NO_RECENT_CHECKIN: no check-in submitted in the last 10 days.
        CheckIn latest = snapshot.latestCheckIn();
        boolean hasRecentCheckIn = latest != null
                && !latest.getSubmittedAt().isBefore(now.minus(10, ChronoUnit.DAYS));
        if (!hasRecentCheckIn) {
            flags.add(AttentionFlag.NO_RECENT_CHECKIN);
        }

        flags.addAll(evaluatePerExerciseFlags(snapshot.lastThreeOccurrencesPerExercise()));
        return flags;
    }

    /**
     * HIGH_RPE_PATTERN: average RPE >= 9 for three consecutive sessions on the same exercise.
     * RECENT_PERFORMANCE_DROP: estimated 1RM in the most recent session is meaningfully
     * lower (>10%) than the best of the two sessions before it.
     *
     * @param occurrencesPerExercise for each exercise, its last three sessions' sets, most recent first
     */
    private Set<AttentionFlag> evaluatePerExerciseFlags(List<List<List<WorkoutSet>>> occurrencesPerExercise) {
        Set<AttentionFlag> flags = EnumSet.noneOf(AttentionFlag.class);
        for (List<List<WorkoutSet>> setsPerOccurrence : occurrencesPerExercise) {
            if (setsPerOccurrence.size() < 3) {
                continue;
            }
            boolean allHighRpe = setsPerOccurrence.stream().allMatch(sets -> {
                BigDecimal avg = progressionService.averageRpe(sets);
                return avg != null && avg.compareTo(HIGH_RPE) >= 0;
            });
            if (allHighRpe) {
                flags.add(AttentionFlag.HIGH_RPE_PATTERN);
            }

            BigDecimal latest1Rm = progressionService.bestEstimated1RmInSet(setsPerOccurrence.get(0));
            BigDecimal prior1Rm = progressionService.bestEstimated1RmInSet(setsPerOccurrence.get(1))
                    .max(progressionService.bestEstimated1RmInSet(setsPerOccurrence.get(2)));
            if (prior1Rm.compareTo(BigDecimal.ZERO) > 0 && latest1Rm.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal dropRatio = prior1Rm.subtract(latest1Rm).divide(prior1Rm, 4, RoundingMode.HALF_UP);
                if (dropRatio.compareTo(PERFORMANCE_DROP) > 0) {
                    flags.add(AttentionFlag.RECENT_PERFORMANCE_DROP);
                }
            }
        }
        return flags;
    }
}

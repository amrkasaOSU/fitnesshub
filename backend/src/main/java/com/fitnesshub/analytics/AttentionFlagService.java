package com.fitnesshub.analytics;

import com.fitnesshub.analytics.dto.AdherenceResult;
import com.fitnesshub.bodyweight.WeightEntry;
import com.fitnesshub.bodyweight.WeightEntryRepository;
import com.fitnesshub.client.ClientProfile;
import com.fitnesshub.client.ClientProfileRepository;
import com.fitnesshub.goal.CheckIn;
import com.fitnesshub.goal.CheckInRepository;
import com.fitnesshub.nutrition.NutritionEntry;
import com.fitnesshub.nutrition.NutritionEntryRepository;
import com.fitnesshub.progress.ProgressionService;
import com.fitnesshub.steps.StepEntry;
import com.fitnesshub.steps.StepEntryRepository;
import com.fitnesshub.workout.WorkoutExercise;
import com.fitnesshub.workout.WorkoutExerciseRepository;
import com.fitnesshub.workout.WorkoutSession;
import com.fitnesshub.workout.WorkoutSessionRepository;
import com.fitnesshub.workout.WorkoutSet;
import com.fitnesshub.workout.WorkoutSetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Deterministic, rule-based coaching signals - not a diagnosis of anything
 * medical. Every threshold here is documented in docs/fitness-calculations.md
 * so a coach can see exactly why a client was flagged.
 */
@Service
public class AttentionFlagService {

    private final WorkoutSessionRepository workoutSessionRepository;
    private final WorkoutExerciseRepository workoutExerciseRepository;
    private final WorkoutSetRepository workoutSetRepository;
    private final WeightEntryRepository weightEntryRepository;
    private final NutritionEntryRepository nutritionEntryRepository;
    private final CheckInRepository checkInRepository;
    private final StepEntryRepository stepEntryRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final AdherenceService adherenceService;
    private final ProgressionService progressionService;

    public AttentionFlagService(WorkoutSessionRepository workoutSessionRepository,
                                 WorkoutExerciseRepository workoutExerciseRepository,
                                 WorkoutSetRepository workoutSetRepository,
                                 WeightEntryRepository weightEntryRepository,
                                 NutritionEntryRepository nutritionEntryRepository,
                                 CheckInRepository checkInRepository,
                                 StepEntryRepository stepEntryRepository,
                                 ClientProfileRepository clientProfileRepository,
                                 AdherenceService adherenceService,
                                 ProgressionService progressionService) {
        this.workoutSessionRepository = workoutSessionRepository;
        this.workoutExerciseRepository = workoutExerciseRepository;
        this.workoutSetRepository = workoutSetRepository;
        this.weightEntryRepository = weightEntryRepository;
        this.nutritionEntryRepository = nutritionEntryRepository;
        this.checkInRepository = checkInRepository;
        this.stepEntryRepository = stepEntryRepository;
        this.clientProfileRepository = clientProfileRepository;
        this.adherenceService = adherenceService;
        this.progressionService = progressionService;
    }

    @Transactional(readOnly = true)
    public Set<AttentionFlag> computeFlags(UUID clientId, ZoneId zone) {
        Set<AttentionFlag> flags = EnumSet.noneOf(AttentionFlag.class);
        LocalDate today = LocalDate.now(zone);
        Instant now = Instant.now();

        // NO_WORKOUT_LOGGED: nothing at all in the last 7 days.
        List<WorkoutSession> recentSessions = workoutSessionRepository
                .findByClientIdAndStartedAtBetween(clientId, now.minus(7, ChronoUnit.DAYS), now);
        if (recentSessions.isEmpty()) {
            flags.add(AttentionFlag.NO_WORKOUT_LOGGED);
        }

        // LOW_ADHERENCE: >=3 planned workouts in the last 7 days and completed/planned < 0.67.
        AdherenceResult adherence = adherenceService.calculateForClient(clientId, today.minusDays(6), today, zone);
        if (adherence.plannedWorkouts() >= 3) {
            BigDecimal ratio = BigDecimal.valueOf(adherence.completedWorkouts())
                    .divide(BigDecimal.valueOf(adherence.plannedWorkouts()), 4, java.math.RoundingMode.HALF_UP);
            if (ratio.compareTo(new BigDecimal("0.67")) < 0) {
                flags.add(AttentionFlag.LOW_ADHERENCE);
            }
        }

        // WEIGHT_TREND_STALLED: at least 3 weeks of weigh-ins with essentially no net change.
        List<WeightEntry> weights = weightEntryRepository.findByClientIdOrderByRecordedAtAsc(clientId);
        List<WeightEntry> lastThreeWeeks = weights.stream()
                .filter(w -> !w.getRecordedAt().isBefore(now.minus(21, ChronoUnit.DAYS)))
                .toList();
        if (lastThreeWeeks.size() >= 3) {
            BigDecimal first = lastThreeWeeks.get(0).getWeight();
            BigDecimal last = lastThreeWeeks.get(lastThreeWeeks.size() - 1).getWeight();
            if (first.subtract(last).abs().compareTo(new BigDecimal("1.0")) <= 0) {
                flags.add(AttentionFlag.WEIGHT_TREND_STALLED);
            }
        }

        // CALORIE_TRACKING_INCONSISTENT: fewer than 4 of the last 7 days logged.
        List<NutritionEntry> nutritionLast7 = nutritionEntryRepository
                .findByClientIdAndDateBetweenOrderByDateAsc(clientId, today.minusDays(6), today);
        if (nutritionLast7.size() < 4) {
            flags.add(AttentionFlag.CALORIE_TRACKING_INCONSISTENT);
        }

        // STEP_TARGET_MISSED: 7-day average steps below 70% of the active goal (needs at least one entry).
        List<StepEntry> stepsLast7 = stepEntryRepository
                .findByClientIdAndDateBetweenOrderByDateAsc(clientId, today.minusDays(6), today);
        if (!stepsLast7.isEmpty()) {
            ClientProfile profile = clientProfileRepository.findByUserId(clientId).orElse(null);
            int goal = (profile != null && profile.getDailyStepTarget() != null) ? profile.getDailyStepTarget() : 10000;
            double avgSteps = stepsLast7.stream().mapToInt(StepEntry::getSteps).average().orElse(0);
            if (avgSteps < goal * 0.7) {
                flags.add(AttentionFlag.STEP_TARGET_MISSED);
            }
        }

        // NO_RECENT_CHECKIN: no check-in submitted in the last 10 days.
        boolean hasRecentCheckIn = checkInRepository.findFirstByClientIdOrderByWeekStartDateDesc(clientId)
                .map(CheckIn::getSubmittedAt)
                .map(submittedAt -> !submittedAt.isBefore(now.minus(10, ChronoUnit.DAYS)))
                .orElse(false);
        if (!hasRecentCheckIn) {
            flags.add(AttentionFlag.NO_RECENT_CHECKIN);
        }

        // HIGH_RPE_PATTERN / RECENT_PERFORMANCE_DROP: look at each exercise's last 3 completed sessions.
        List<WorkoutSession> completed = workoutSessionRepository.findCompletedForClient(clientId);
        flags.addAll(evaluatePerExerciseFlags(completed));

        return flags;
    }

    /**
     * HIGH_RPE_PATTERN: average RPE >= 9 for three consecutive sessions on the same exercise.
     * RECENT_PERFORMANCE_DROP: estimated 1RM in the most recent session is meaningfully
     * lower (>10%) than the best of the two sessions before it.
     */
    private Set<AttentionFlag> evaluatePerExerciseFlags(List<WorkoutSession> completedSessionsMostRecentFirst) {
        Set<AttentionFlag> flags = EnumSet.noneOf(AttentionFlag.class);
        if (completedSessionsMostRecentFirst.isEmpty()) {
            return flags;
        }
        List<UUID> sessionIds = completedSessionsMostRecentFirst.stream().map(WorkoutSession::getId).toList();
        List<WorkoutExercise> allWorkoutExercises = sessionIds.stream()
                .flatMap(id -> workoutExerciseRepository.findByWorkoutSessionIdOrderByOrderIndexAsc(id).stream())
                .toList();

        var bySessionThenExercise = allWorkoutExercises.stream()
                .collect(Collectors.groupingBy(WorkoutExercise::getExerciseId));

        for (var entry : bySessionThenExercise.entrySet()) {
            List<WorkoutExercise> occurrences = entry.getValue().stream()
                    .sorted(Comparator.comparing(we -> sessionIds.indexOf(we.getWorkoutSessionId())))
                    .limit(3)
                    .toList();
            if (occurrences.size() < 3) {
                continue;
            }
            List<List<WorkoutSet>> setsPerOccurrence = new ArrayList<>();
            for (WorkoutExercise we : occurrences) {
                setsPerOccurrence.add(workoutSetRepository.findByWorkoutExerciseIdOrderBySetNumberAsc(we.getId()));
            }

            boolean allHighRpe = setsPerOccurrence.stream().allMatch(sets -> {
                BigDecimal avg = progressionService.averageRpe(sets);
                return avg != null && avg.compareTo(new BigDecimal("9.0")) >= 0;
            });
            if (allHighRpe) {
                flags.add(AttentionFlag.HIGH_RPE_PATTERN);
            }

            BigDecimal latest1Rm = progressionService.bestEstimated1RmInSet(setsPerOccurrence.get(0));
            BigDecimal prior1Rm = progressionService.bestEstimated1RmInSet(setsPerOccurrence.get(1))
                    .max(progressionService.bestEstimated1RmInSet(setsPerOccurrence.get(2)));
            if (prior1Rm.compareTo(BigDecimal.ZERO) > 0 && latest1Rm.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal dropRatio = prior1Rm.subtract(latest1Rm).divide(prior1Rm, 4, java.math.RoundingMode.HALF_UP);
                if (dropRatio.compareTo(new BigDecimal("0.10")) > 0) {
                    flags.add(AttentionFlag.RECENT_PERFORMANCE_DROP);
                }
            }
        }
        return flags;
    }
}

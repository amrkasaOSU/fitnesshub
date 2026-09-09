package com.fitnesshub.ai;

import com.fitnesshub.ai.dto.FitnessContext;
import com.fitnesshub.analytics.AdherenceService;
import com.fitnesshub.analytics.dto.AdherenceResult;
import com.fitnesshub.bodyweight.WeightService;
import com.fitnesshub.exercise.Exercise;
import com.fitnesshub.exercise.ExerciseRepository;
import com.fitnesshub.goal.GoalRepository;
import com.fitnesshub.goal.GoalService;
import com.fitnesshub.goal.GoalStatus;
import com.fitnesshub.nutrition.NutritionService;
import com.fitnesshub.progress.ExerciseProgressService;
import com.fitnesshub.progress.PersonalRecordService;
import com.fitnesshub.progress.dto.ExerciseProgressSummary;
import com.fitnesshub.steps.StepService;
import com.fitnesshub.user.User;
import com.fitnesshub.user.UserRepository;
import com.fitnesshub.workout.WorkoutSessionRepository;
import com.fitnesshub.workout.WorkoutSessionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The "retrieve relevant data" step of the AI pipeline (spec section 72/73):
 * given a client and a raw question, this pulls exactly the structured data
 * needed to answer it - never the whole database, never someone else's data.
 */
@Service
public class FitnessContextService {

    private final ExerciseRepository exerciseRepository;
    private final ExerciseProgressService exerciseProgressService;
    private final PersonalRecordService personalRecordService;
    private final AdherenceService adherenceService;
    private final WeightService weightService;
    private final NutritionService nutritionService;
    private final StepService stepService;
    private final GoalRepository goalRepository;
    private final GoalService goalService;
    private final WorkoutSessionRepository workoutSessionRepository;
    private final UserRepository userRepository;

    public FitnessContextService(ExerciseRepository exerciseRepository,
                                  ExerciseProgressService exerciseProgressService,
                                  PersonalRecordService personalRecordService,
                                  AdherenceService adherenceService,
                                  WeightService weightService,
                                  NutritionService nutritionService,
                                  StepService stepService,
                                  GoalRepository goalRepository,
                                  GoalService goalService,
                                  WorkoutSessionRepository workoutSessionRepository,
                                  UserRepository userRepository) {
        this.exerciseRepository = exerciseRepository;
        this.exerciseProgressService = exerciseProgressService;
        this.personalRecordService = personalRecordService;
        this.adherenceService = adherenceService;
        this.weightService = weightService;
        this.nutritionService = nutritionService;
        this.stepService = stepService;
        this.goalRepository = goalRepository;
        this.goalService = goalService;
        this.workoutSessionRepository = workoutSessionRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public FitnessContext buildContext(UUID clientId, String question) {
        User user = userRepository.findById(clientId).orElseThrow();
        ZoneId zone = safeZone(user.getTimezone());
        LocalDate today = LocalDate.now(zone);

        Exercise matchedExercise = question == null ? null : findMentionedExercise(question);
        ExerciseProgressSummary exerciseProgress = matchedExercise == null ? null
                : exerciseProgressService.getProgress(clientId, matchedExercise.getId());

        AdherenceResult adherence = adherenceService.calculateForClient(clientId, today.minusDays(6), today, zone);
        var recentPrs = personalRecordService.recentForClient(clientId, 5);
        var weight = weightService.dashboard(clientId);
        var nutrition = nutritionService.dashboard(clientId);
        var steps = stepService.dashboard(clientId);
        var goals = goalRepository.findByClientIdAndStatus(clientId, GoalStatus.ACTIVE).stream()
                .map(goalService::toDto).toList();

        Instant since30 = Instant.now().minus(30, ChronoUnit.DAYS);
        int completed30 = (int) workoutSessionRepository
                .findByClientIdAndStartedAtBetween(clientId, since30, Instant.now()).stream()
                .filter(s -> s.getStatus() == WorkoutSessionStatus.COMPLETED)
                .count();

        return new FitnessContext(user.getFirstName(), matchedExercise == null ? null : matchedExercise.getName(),
                exerciseProgress, recentPrs, adherence, weight, nutrition, steps, goals, completed30);
    }

    /** Naive but effective: the exercise library is small enough that a case-insensitive substring match works. */
    private Exercise findMentionedExercise(String question) {
        String lower = question.toLowerCase();
        List<Exercise> all = exerciseRepository.findAll();
        return all.stream()
                .filter(e -> lower.contains(e.getName().toLowerCase()))
                .max((a, b) -> Integer.compare(a.getName().length(), b.getName().length()))
                .orElse(null);
    }

    private ZoneId safeZone(String tz) {
        try {
            return tz == null ? ZoneId.of("UTC") : ZoneId.of(tz);
        } catch (Exception e) {
            return ZoneId.of("UTC");
        }
    }
}

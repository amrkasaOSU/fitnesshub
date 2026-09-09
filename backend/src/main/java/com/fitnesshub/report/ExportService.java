package com.fitnesshub.report;

import com.fitnesshub.bodyweight.WeightEntry;
import com.fitnesshub.bodyweight.WeightEntryRepository;
import com.fitnesshub.common.exception.ForbiddenException;
import com.fitnesshub.nutrition.NutritionEntry;
import com.fitnesshub.nutrition.NutritionEntryRepository;
import com.fitnesshub.security.AuthorizationService;
import com.fitnesshub.security.CurrentUser;
import com.fitnesshub.steps.StepEntry;
import com.fitnesshub.steps.StepEntryRepository;
import com.fitnesshub.subscription.SubscriptionService;
import com.fitnesshub.workout.WorkoutExercise;
import com.fitnesshub.workout.WorkoutExerciseRepository;
import com.fitnesshub.workout.WorkoutSession;
import com.fitnesshub.workout.WorkoutSessionRepository;
import com.fitnesshub.workout.WorkoutSet;
import com.fitnesshub.workout.WorkoutSetRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * CSV export for premium clients and coaches (spec section 159). A coach may
 * always export their own clients' data as part of the service they're
 * providing; a client needs an active premium subscription.
 */
@Service
public class ExportService {

    private final WeightEntryRepository weightEntryRepository;
    private final StepEntryRepository stepEntryRepository;
    private final NutritionEntryRepository nutritionEntryRepository;
    private final WorkoutSessionRepository workoutSessionRepository;
    private final WorkoutExerciseRepository workoutExerciseRepository;
    private final WorkoutSetRepository workoutSetRepository;
    private final AuthorizationService authorizationService;
    private final SubscriptionService subscriptionService;
    private final CurrentUser currentUser;

    public ExportService(WeightEntryRepository weightEntryRepository, StepEntryRepository stepEntryRepository,
                          NutritionEntryRepository nutritionEntryRepository,
                          WorkoutSessionRepository workoutSessionRepository,
                          WorkoutExerciseRepository workoutExerciseRepository,
                          WorkoutSetRepository workoutSetRepository, AuthorizationService authorizationService,
                          SubscriptionService subscriptionService, CurrentUser currentUser) {
        this.weightEntryRepository = weightEntryRepository;
        this.stepEntryRepository = stepEntryRepository;
        this.nutritionEntryRepository = nutritionEntryRepository;
        this.workoutSessionRepository = workoutSessionRepository;
        this.workoutExerciseRepository = workoutExerciseRepository;
        this.workoutSetRepository = workoutSetRepository;
        this.authorizationService = authorizationService;
        this.subscriptionService = subscriptionService;
        this.currentUser = currentUser;
    }

    private UUID resolveExportableClientId(UUID requestedClientId) {
        UUID clientId = authorizationService.resolveClientId(requestedClientId);
        if (currentUser.isClient() && !subscriptionService.isPaid(clientId)) {
            throw new ForbiddenException("Data export is a premium feature. Upgrade to export your history.");
        }
        return clientId;
    }

    @Transactional(readOnly = true)
    public String exportWeightCsv(UUID requestedClientId) {
        UUID clientId = resolveExportableClientId(requestedClientId);
        List<WeightEntry> entries = weightEntryRepository.findByClientIdOrderByRecordedAtAsc(clientId);
        List<List<String>> rows = entries.stream().map(e -> List.of(
                e.getRecordedAt().toString(), e.getWeight().toPlainString(),
                e.getNotes() == null ? "" : e.getNotes())).toList();
        return CsvWriter.toCsv(List.of("recorded_at", "weight", "notes"), rows);
    }

    @Transactional(readOnly = true)
    public String exportStepsCsv(UUID requestedClientId) {
        UUID clientId = resolveExportableClientId(requestedClientId);
        List<StepEntry> entries = stepEntryRepository
                .findByClientIdOrderByDateDesc(clientId, Pageable.unpaged(Sort.by("date"))).getContent();
        List<List<String>> rows = entries.stream().map(e -> List.of(
                e.getDate().toString(), String.valueOf(e.getSteps()), e.getSource().name())).toList();
        return CsvWriter.toCsv(List.of("date", "steps", "source"), rows);
    }

    @Transactional(readOnly = true)
    public String exportNutritionCsv(UUID requestedClientId) {
        UUID clientId = resolveExportableClientId(requestedClientId);
        List<NutritionEntry> entries = nutritionEntryRepository
                .findByClientIdOrderByDateDesc(clientId, Pageable.unpaged(Sort.by("date"))).getContent();
        List<List<String>> rows = entries.stream().map(e -> List.of(
                e.getDate().toString(), e.getCalories().toPlainString(), e.getProteinGrams().toPlainString(),
                str(e.getCarbohydratesGrams()), str(e.getFatGrams()), str(e.getFiberGrams()),
                e.getWaterMl() == null ? "" : e.getWaterMl().toString())).toList();
        return CsvWriter.toCsv(List.of("date", "calories", "protein_g", "carbs_g", "fat_g", "fiber_g", "water_ml"), rows);
    }

    @Transactional(readOnly = true)
    public String exportWorkoutsCsv(UUID requestedClientId) {
        UUID clientId = resolveExportableClientId(requestedClientId);
        List<WorkoutSession> sessions = workoutSessionRepository
                .findByClientIdOrderByStartedAtDesc(clientId, Pageable.unpaged(Sort.by("startedAt"))).getContent();

        List<List<String>> rows = new java.util.ArrayList<>();
        for (WorkoutSession session : sessions) {
            List<WorkoutExercise> exercises = workoutExerciseRepository
                    .findByWorkoutSessionIdOrderByOrderIndexAsc(session.getId());
            for (WorkoutExercise we : exercises) {
                List<WorkoutSet> sets = workoutSetRepository.findByWorkoutExerciseIdOrderBySetNumberAsc(we.getId());
                for (WorkoutSet set : sets) {
                    rows.add(List.of(
                            session.getStartedAt().toString(),
                            session.getStatus().name(),
                            we.getExerciseId().toString(),
                            String.valueOf(set.getSetNumber()),
                            set.getWeight().toPlainString(),
                            String.valueOf(set.getReps()),
                            set.getRpe() == null ? "" : set.getRpe().toPlainString(),
                            String.valueOf(set.isWarmup()),
                            String.valueOf(set.isFailure())
                    ));
                }
            }
        }
        return CsvWriter.toCsv(List.of("session_started_at", "session_status", "exercise_id", "set_number",
                "weight", "reps", "rpe", "is_warmup", "is_failure"), rows);
    }

    private String str(java.math.BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }
}

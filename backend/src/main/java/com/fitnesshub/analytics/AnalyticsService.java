package com.fitnesshub.analytics;

import com.fitnesshub.analytics.dto.AdherenceResult;
import com.fitnesshub.analytics.dto.TrainingSummaryDto;
import com.fitnesshub.progress.PersonalRecordRepository;
import com.fitnesshub.progress.ProgressionService;
import com.fitnesshub.security.AuthorizationService;
import com.fitnesshub.user.User;
import com.fitnesshub.user.UserRepository;
import com.fitnesshub.workout.WorkoutExercise;
import com.fitnesshub.workout.WorkoutExerciseRepository;
import com.fitnesshub.workout.WorkoutSession;
import com.fitnesshub.workout.WorkoutSessionRepository;
import com.fitnesshub.workout.WorkoutSessionStatus;
import com.fitnesshub.workout.WorkoutSet;
import com.fitnesshub.workout.WorkoutSetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class AnalyticsService {

    private final WorkoutSessionRepository workoutSessionRepository;
    private final WorkoutExerciseRepository workoutExerciseRepository;
    private final WorkoutSetRepository workoutSetRepository;
    private final PersonalRecordRepository personalRecordRepository;
    private final AdherenceService adherenceService;
    private final ProgressionService progressionService;
    private final AuthorizationService authorizationService;
    private final UserRepository userRepository;

    public AnalyticsService(WorkoutSessionRepository workoutSessionRepository,
                             WorkoutExerciseRepository workoutExerciseRepository,
                             WorkoutSetRepository workoutSetRepository,
                             PersonalRecordRepository personalRecordRepository,
                             AdherenceService adherenceService,
                             ProgressionService progressionService,
                             AuthorizationService authorizationService,
                             UserRepository userRepository) {
        this.workoutSessionRepository = workoutSessionRepository;
        this.workoutExerciseRepository = workoutExerciseRepository;
        this.workoutSetRepository = workoutSetRepository;
        this.personalRecordRepository = personalRecordRepository;
        this.adherenceService = adherenceService;
        this.progressionService = progressionService;
        this.authorizationService = authorizationService;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public TrainingSummaryDto trainingSummary(UUID requestedClientId, LocalDate from, LocalDate to) {
        UUID clientId = authorizationService.resolveClientId(requestedClientId);
        ZoneId zone = zoneFor(clientId);

        Instant windowStart = from.atStartOfDay(zone).toInstant();
        Instant windowEnd = to.plusDays(1).atStartOfDay(zone).toInstant();
        List<WorkoutSession> sessions = workoutSessionRepository
                .findByClientIdAndStartedAtBetween(clientId, windowStart, windowEnd).stream()
                .filter(s -> s.getStatus() == WorkoutSessionStatus.COMPLETED)
                .toList();

        List<WorkoutExercise> exercises = sessions.stream()
                .flatMap(s -> workoutExerciseRepository.findByWorkoutSessionIdOrderByOrderIndexAsc(s.getId()).stream())
                .toList();
        List<WorkoutSet> sets = exercises.isEmpty() ? List.of()
                : workoutSetRepository.findByWorkoutExerciseIdInOrderBySetNumberAsc(
                        exercises.stream().map(WorkoutExercise::getId).toList());

        int totalSets = (int) sets.stream().filter(s -> !s.isWarmup()).count();
        int totalReps = sets.stream().filter(s -> !s.isWarmup()).mapToInt(WorkoutSet::getReps).sum();
        BigDecimal totalVolume = progressionService.totalVolume(sets);
        BigDecimal avgRpe = progressionService.averageRpe(sets);
        BigDecimal avgRir = averageRir(sets);

        BigDecimal avgDurationMinutes = sessions.stream()
                .map(WorkoutSession::getDurationSeconds)
                .filter(d -> d != null)
                .mapToInt(Integer::intValue)
                .average()
                .stream().mapToObj(avg -> BigDecimal.valueOf(avg / 60.0).setScale(1, RoundingMode.HALF_UP))
                .findFirst().orElse(null);

        int prCount = personalRecordRepository.findByClientIdAndAchievedAtBetween(clientId, windowStart, windowEnd).size();

        AdherenceResult adherence = adherenceService.calculateForClient(clientId, from, to, zone);

        return new TrainingSummaryDto(sessions.size(), adherence.plannedWorkouts(), adherence.adherencePercentage(),
                totalSets, totalReps, totalVolume, avgDurationMinutes, avgRpe, avgRir, prCount);
    }

    private BigDecimal averageRir(List<WorkoutSet> sets) {
        List<BigDecimal> rirs = sets.stream().map(WorkoutSet::getRir).filter(r -> r != null).toList();
        if (rirs.isEmpty()) {
            return null;
        }
        return rirs.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(rirs.size()), 2, RoundingMode.HALF_UP);
    }

    private ZoneId zoneFor(UUID clientId) {
        User user = userRepository.findById(clientId).orElse(null);
        try {
            return user == null ? ZoneId.of("UTC") : ZoneId.of(user.getTimezone());
        } catch (Exception e) {
            return ZoneId.of("UTC");
        }
    }
}

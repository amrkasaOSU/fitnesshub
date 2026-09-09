package com.fitnesshub.progress;

import com.fitnesshub.progress.dto.ExerciseProgressSummary;
import com.fitnesshub.security.AuthorizationService;
import com.fitnesshub.workout.WorkoutExercise;
import com.fitnesshub.workout.WorkoutExerciseRepository;
import com.fitnesshub.workout.WorkoutSession;
import com.fitnesshub.workout.WorkoutSessionRepository;
import com.fitnesshub.workout.WorkoutSet;
import com.fitnesshub.workout.WorkoutSetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ExerciseProgressService {

    private final WorkoutSessionRepository workoutSessionRepository;
    private final WorkoutExerciseRepository workoutExerciseRepository;
    private final WorkoutSetRepository workoutSetRepository;
    private final ProgressionService progressionService;
    private final AuthorizationService authorizationService;

    public ExerciseProgressService(WorkoutSessionRepository workoutSessionRepository,
                                    WorkoutExerciseRepository workoutExerciseRepository,
                                    WorkoutSetRepository workoutSetRepository,
                                    ProgressionService progressionService,
                                    AuthorizationService authorizationService) {
        this.workoutSessionRepository = workoutSessionRepository;
        this.workoutExerciseRepository = workoutExerciseRepository;
        this.workoutSetRepository = workoutSetRepository;
        this.progressionService = progressionService;
        this.authorizationService = authorizationService;
    }

    @Transactional(readOnly = true)
    public ExerciseProgressSummary getProgress(UUID requestedClientId, UUID exerciseId) {
        UUID clientId = authorizationService.resolveClientId(requestedClientId);

        List<WorkoutSession> completedSessions = workoutSessionRepository.findCompletedForClient(clientId);
        if (completedSessions.isEmpty()) {
            return progressionService.summarize(List.of());
        }

        List<UUID> sessionIds = completedSessions.stream().map(WorkoutSession::getId).toList();
        List<WorkoutExercise> matching = workoutExerciseRepository
                .findByExerciseIdAndWorkoutSessionIdIn(exerciseId, sessionIds);
        if (matching.isEmpty()) {
            return progressionService.summarize(List.of());
        }

        Map<UUID, WorkoutSession> sessionsById = completedSessions.stream()
                .collect(Collectors.toMap(WorkoutSession::getId, s -> s));
        Map<UUID, List<WorkoutSet>> setsByWorkoutExercise = workoutSetRepository
                .findByWorkoutExerciseIdInOrderBySetNumberAsc(matching.stream().map(WorkoutExercise::getId).toList())
                .stream().collect(Collectors.groupingBy(WorkoutSet::getWorkoutExerciseId));

        List<ProgressionService.SessionSets> sessions = matching.stream()
                .map(we -> new ProgressionService.SessionSets(
                        sessionsById.get(we.getWorkoutSessionId()).getStartedAt(),
                        setsByWorkoutExercise.getOrDefault(we.getId(), List.of())))
                .sorted(Comparator.comparing(ProgressionService.SessionSets::sessionDate).reversed())
                .toList();

        return progressionService.summarize(sessions);
    }
}

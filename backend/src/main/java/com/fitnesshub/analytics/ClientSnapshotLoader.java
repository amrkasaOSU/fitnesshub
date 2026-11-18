package com.fitnesshub.analytics;

import com.fitnesshub.bodyweight.WeightEntry;
import com.fitnesshub.bodyweight.WeightEntryRepository;
import com.fitnesshub.client.ClientProfile;
import com.fitnesshub.client.ClientProfileRepository;
import com.fitnesshub.goal.CheckIn;
import com.fitnesshub.goal.CheckInRepository;
import com.fitnesshub.nutrition.NutritionEntry;
import com.fitnesshub.nutrition.NutritionEntryRepository;
import com.fitnesshub.program.ClientProgram;
import com.fitnesshub.program.ClientProgramRepository;
import com.fitnesshub.program.ClientProgramStatus;
import com.fitnesshub.program.ProgramDay;
import com.fitnesshub.program.ProgramDayRepository;
import com.fitnesshub.program.ProgramExercise;
import com.fitnesshub.program.ProgramExerciseRepository;
import com.fitnesshub.steps.StepEntry;
import com.fitnesshub.steps.StepEntryRepository;
import com.fitnesshub.user.User;
import com.fitnesshub.user.UserRepository;
import com.fitnesshub.workout.WorkoutExerciseRepository;
import com.fitnesshub.workout.WorkoutSession;
import com.fitnesshub.workout.WorkoutSessionRepository;
import com.fitnesshub.workout.WorkoutSet;
import com.fitnesshub.workout.WorkoutSetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Loads {@link ClientSnapshot}s for any number of clients in a fixed number of
 * queries - one per table, each with {@code client_id IN (...)} - instead of
 * the dozens of queries per client the roster used to issue.
 *
 * <p>The windows are deliberately a little wider than any single calculation
 * needs, because "today" differs by client timezone and each calculation
 * applies its own exact bounds in memory afterwards. Over-fetching a day is
 * harmless; under-fetching would silently change a number.
 */
@Service
public class ClientSnapshotLoader {

    /** Covers the two completed Monday-Sunday weeks the weight trend needs, plus the 21-day stall window. */
    private static final int WEIGHT_LOOKBACK_DAYS = 23;
    /** Covers the 7-day adherence and "no workout logged" windows in any timezone. */
    private static final int SESSION_LOOKBACK_DAYS = 9;
    /** Covers "the last 7 days" of steps and nutrition in any timezone. */
    private static final int DAILY_LOG_LOOKBACK_DAYS = 8;

    private final UserRepository userRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final WeightEntryRepository weightEntryRepository;
    private final StepEntryRepository stepEntryRepository;
    private final NutritionEntryRepository nutritionEntryRepository;
    private final ClientProgramRepository clientProgramRepository;
    private final ProgramDayRepository programDayRepository;
    private final ProgramExerciseRepository programExerciseRepository;
    private final WorkoutSessionRepository workoutSessionRepository;
    private final WorkoutExerciseRepository workoutExerciseRepository;
    private final WorkoutSetRepository workoutSetRepository;
    private final CheckInRepository checkInRepository;

    public ClientSnapshotLoader(UserRepository userRepository,
                                ClientProfileRepository clientProfileRepository,
                                WeightEntryRepository weightEntryRepository,
                                StepEntryRepository stepEntryRepository,
                                NutritionEntryRepository nutritionEntryRepository,
                                ClientProgramRepository clientProgramRepository,
                                ProgramDayRepository programDayRepository,
                                ProgramExerciseRepository programExerciseRepository,
                                WorkoutSessionRepository workoutSessionRepository,
                                WorkoutExerciseRepository workoutExerciseRepository,
                                WorkoutSetRepository workoutSetRepository,
                                CheckInRepository checkInRepository) {
        this.userRepository = userRepository;
        this.clientProfileRepository = clientProfileRepository;
        this.weightEntryRepository = weightEntryRepository;
        this.stepEntryRepository = stepEntryRepository;
        this.nutritionEntryRepository = nutritionEntryRepository;
        this.clientProgramRepository = clientProgramRepository;
        this.programDayRepository = programDayRepository;
        this.programExerciseRepository = programExerciseRepository;
        this.workoutSessionRepository = workoutSessionRepository;
        this.workoutExerciseRepository = workoutExerciseRepository;
        this.workoutSetRepository = workoutSetRepository;
        this.checkInRepository = checkInRepository;
    }

    /** Snapshots keyed by client id, in the order the ids were given. Unknown ids are skipped. */
    @Transactional(readOnly = true)
    public Map<UUID, ClientSnapshot> load(List<UUID> clientIds) {
        if (clientIds.isEmpty()) {
            return Map.of();
        }
        Instant now = Instant.now();
        LocalDate utcToday = LocalDate.now(ZoneOffset.UTC);

        Map<UUID, User> users = userRepository.findAllById(clientIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<UUID, ClientProfile> profiles = clientProfileRepository.findByUserIdIn(clientIds).stream()
                .collect(Collectors.toMap(ClientProfile::getUserId, Function.identity()));

        Map<UUID, List<WeightEntry>> recentWeights = weightEntryRepository
                .findByClientIdInAndRecordedAtGreaterThanEqualOrderByRecordedAtAsc(
                        clientIds, now.minus(WEIGHT_LOOKBACK_DAYS, ChronoUnit.DAYS))
                .stream().collect(Collectors.groupingBy(WeightEntry::getClientId));
        Map<UUID, WeightEntry> latestWeights = weightEntryRepository.findLatestPerClient(clientIds).stream()
                .collect(Collectors.toMap(WeightEntry::getClientId, Function.identity()));

        LocalDate dailyFrom = utcToday.minusDays(DAILY_LOG_LOOKBACK_DAYS);
        LocalDate dailyTo = utcToday.plusDays(1);
        Map<UUID, List<StepEntry>> steps = stepEntryRepository
                .findByClientIdInAndDateBetweenOrderByDateAsc(clientIds, dailyFrom, dailyTo)
                .stream().collect(Collectors.groupingBy(StepEntry::getClientId));
        Map<UUID, List<NutritionEntry>> nutrition = nutritionEntryRepository
                .findByClientIdInAndDateBetweenOrderByDateAsc(clientIds, dailyFrom, dailyTo)
                .stream().collect(Collectors.groupingBy(NutritionEntry::getClientId));

        Map<UUID, ClientProgram> activePrograms = activeProgramPerClient(clientIds);
        Map<UUID, List<Boolean>> cyclesByProgram = cyclesFor(activePrograms.values().stream()
                .map(ClientProgram::getProgramId).distinct().toList());

        Map<UUID, List<WorkoutSession>> recentSessions = workoutSessionRepository
                .findByClientIdInAndStartedAtGreaterThanEqual(
                        clientIds, now.minus(SESSION_LOOKBACK_DAYS, ChronoUnit.DAYS))
                .stream().collect(Collectors.groupingBy(WorkoutSession::getClientId));
        Map<UUID, Instant> lastCompleted = workoutSessionRepository.findLatestCompletedPerClient(clientIds).stream()
                .collect(Collectors.toMap(WorkoutSession::getClientId, WorkoutSession::getStartedAt));

        Map<UUID, CheckIn> latestCheckIns = checkInRepository.findLatestPerClient(clientIds).stream()
                .collect(Collectors.toMap(CheckIn::getClientId, Function.identity()));

        Map<UUID, List<List<List<WorkoutSet>>>> occurrences = lastThreeOccurrences(clientIds);

        Map<UUID, ClientSnapshot> snapshots = new LinkedHashMap<>();
        for (UUID clientId : clientIds) {
            User user = users.get(clientId);
            if (user == null) {
                continue;
            }
            ClientProgram program = activePrograms.get(clientId);
            snapshots.put(clientId, new ClientSnapshot(
                    user,
                    profiles.get(clientId),
                    recentWeights.getOrDefault(clientId, List.of()),
                    latestWeights.get(clientId),
                    steps.getOrDefault(clientId, List.of()),
                    nutrition.getOrDefault(clientId, List.of()),
                    program,
                    program == null ? List.of() : cyclesByProgram.getOrDefault(program.getProgramId(), List.of()),
                    recentSessions.getOrDefault(clientId, List.of()),
                    lastCompleted.get(clientId),
                    latestCheckIns.get(clientId),
                    occurrences.getOrDefault(clientId, List.of())));
        }
        return snapshots;
    }

    /** A client can only have one ACTIVE assignment in practice; if data ever disagrees, the newest wins. */
    private Map<UUID, ClientProgram> activeProgramPerClient(List<UUID> clientIds) {
        Map<UUID, ClientProgram> newest = new LinkedHashMap<>();
        for (ClientProgram cp : clientProgramRepository.findByClientIdInAndStatus(clientIds, ClientProgramStatus.ACTIVE)) {
            newest.merge(cp.getClientId(), cp,
                    (a, b) -> b.getStartDate().isAfter(a.getStartDate()) ? b : a);
        }
        return newest;
    }

    /**
     * For each program, one flag per cycle day (in day-number order): does that
     * day prescribe any exercise? A day with none is a rest day and is never
     * "planned" work for adherence.
     */
    public Map<UUID, List<Boolean>> cyclesFor(List<UUID> programIds) {
        if (programIds.isEmpty()) {
            return Map.of();
        }
        List<ProgramDay> days = programDayRepository.findByProgramIdInOrderByDayNumberAsc(programIds);
        Set<UUID> daysWithExercises = days.isEmpty() ? Set.of()
                : programExerciseRepository
                        .findByProgramDayIdInOrderByOrderIndexAsc(days.stream().map(ProgramDay::getId).toList())
                        .stream().map(ProgramExercise::getProgramDayId).collect(Collectors.toCollection(HashSet::new));
        Map<UUID, List<Boolean>> cycles = new LinkedHashMap<>();
        for (ProgramDay day : days) {
            cycles.computeIfAbsent(day.getProgramId(), id -> new ArrayList<>())
                    .add(daysWithExercises.contains(day.getId()));
        }
        return cycles;
    }

    private Map<UUID, List<List<List<WorkoutSet>>>> lastThreeOccurrences(List<UUID> clientIds) {
        // Rows arrive ordered by client, exercise, recency (1 = most recent).
        List<Object[]> rows = workoutExerciseRepository.findLastThreeOccurrencesPerExercise(clientIds);
        if (rows.isEmpty()) {
            return Map.of();
        }
        List<UUID> workoutExerciseIds = rows.stream().map(r -> (UUID) r[0]).toList();
        Map<UUID, List<WorkoutSet>> setsByWorkoutExercise = workoutSetRepository
                .findByWorkoutExerciseIdInOrderBySetNumberAsc(workoutExerciseIds)
                .stream().collect(Collectors.groupingBy(WorkoutSet::getWorkoutExerciseId));

        Map<UUID, Map<UUID, List<List<WorkoutSet>>>> byClientThenExercise = new LinkedHashMap<>();
        for (Object[] row : rows) {
            UUID workoutExerciseId = (UUID) row[0];
            UUID clientId = (UUID) row[1];
            UUID exerciseId = (UUID) row[2];
            byClientThenExercise
                    .computeIfAbsent(clientId, id -> new LinkedHashMap<>())
                    .computeIfAbsent(exerciseId, id -> new ArrayList<>())
                    .add(setsByWorkoutExercise.getOrDefault(workoutExerciseId, List.of()));
        }
        Map<UUID, List<List<List<WorkoutSet>>>> result = new LinkedHashMap<>();
        byClientThenExercise.forEach((clientId, perExercise) ->
                result.put(clientId, new ArrayList<>(perExercise.values())));
        return result;
    }

    /** Newest first; used by callers that want a stable order for a single client's sessions. */
    static List<WorkoutSession> newestFirst(List<WorkoutSession> sessions) {
        return sessions.stream().sorted(Comparator.comparing(WorkoutSession::getStartedAt).reversed()).toList();
    }
}

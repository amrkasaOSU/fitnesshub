package com.fitnesshub.workout;

import com.fitnesshub.audit.AuditAction;
import com.fitnesshub.audit.AuditService;
import com.fitnesshub.common.exception.ConflictException;
import com.fitnesshub.common.exception.NotFoundException;
import com.fitnesshub.exercise.Exercise;
import com.fitnesshub.exercise.ExerciseCatalog;
import com.fitnesshub.exercise.ExerciseRepository;
import com.fitnesshub.coach.CoachClientRepository;
import com.fitnesshub.coach.CoachClientStatus;
import com.fitnesshub.notification.NotificationService;
import com.fitnesshub.notification.NotificationType;
import com.fitnesshub.program.ClientProgram;
import com.fitnesshub.program.ClientProgramRepository;
import com.fitnesshub.program.ClientProgramStatus;
import com.fitnesshub.program.Program;
import com.fitnesshub.program.ProgramDay;
import com.fitnesshub.program.ProgramDayRepository;
import com.fitnesshub.program.ProgramExercise;
import com.fitnesshub.program.ProgramExerciseRepository;
import com.fitnesshub.program.ProgramRepository;
import com.fitnesshub.progress.PersonalRecord;
import com.fitnesshub.progress.PersonalRecordRepository;
import com.fitnesshub.progress.PersonalRecordService;
import com.fitnesshub.progress.ProgressionService;
import com.fitnesshub.security.AuthorizationService;
import com.fitnesshub.security.CurrentUser;
import com.fitnesshub.workout.dto.CompleteWorkoutRequest;
import com.fitnesshub.workout.dto.LogSetRequest;
import com.fitnesshub.workout.dto.PreviousPerformanceDto;
import com.fitnesshub.workout.dto.SetCompletionResult;
import com.fitnesshub.user.UserRepository;
import com.fitnesshub.workout.dto.UpdateSetRequest;
import com.fitnesshub.workout.dto.WeekDayDto;
import com.fitnesshub.workout.dto.WorkoutExerciseDto;
import com.fitnesshub.workout.dto.WorkoutFeedbackDto;
import com.fitnesshub.workout.dto.WorkoutSessionDto;
import com.fitnesshub.workout.dto.WorkoutSetDto;
import com.fitnesshub.workout.dto.WorkoutSummaryDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class WorkoutService {

    private final WorkoutSessionRepository sessionRepository;
    private final WorkoutExerciseRepository workoutExerciseRepository;
    private final WorkoutSetRepository workoutSetRepository;
    private final ClientProgramRepository clientProgramRepository;
    private final ProgramRepository programRepository;
    private final ProgramDayRepository programDayRepository;
    private final ProgramExerciseRepository programExerciseRepository;
    private final ExerciseRepository exerciseRepository;
    private final ExerciseCatalog exerciseCatalog;
    private final PersonalRecordService personalRecordService;
    private final PersonalRecordRepository personalRecordRepository;
    private final ProgressionService progressionService;
    private final WorkoutFeedbackRepository workoutFeedbackRepository;
    private final NotificationService notificationService;
    private final CoachClientRepository coachClientRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public WorkoutService(WorkoutSessionRepository sessionRepository,
                           WorkoutExerciseRepository workoutExerciseRepository,
                           WorkoutSetRepository workoutSetRepository,
                           ClientProgramRepository clientProgramRepository,
                           ProgramRepository programRepository,
                           ProgramDayRepository programDayRepository,
                           ProgramExerciseRepository programExerciseRepository,
                           ExerciseRepository exerciseRepository,
                           ExerciseCatalog exerciseCatalog,
                           PersonalRecordService personalRecordService,
                           PersonalRecordRepository personalRecordRepository,
                           ProgressionService progressionService,
                           WorkoutFeedbackRepository workoutFeedbackRepository,
                           NotificationService notificationService,
                           CoachClientRepository coachClientRepository,
                           UserRepository userRepository,
                           CurrentUser currentUser,
                           AuthorizationService authorizationService,
                           AuditService auditService) {
        this.sessionRepository = sessionRepository;
        this.workoutExerciseRepository = workoutExerciseRepository;
        this.workoutSetRepository = workoutSetRepository;
        this.clientProgramRepository = clientProgramRepository;
        this.programRepository = programRepository;
        this.programDayRepository = programDayRepository;
        this.programExerciseRepository = programExerciseRepository;
        this.exerciseRepository = exerciseRepository;
        this.exerciseCatalog = exerciseCatalog;
        this.personalRecordService = personalRecordService;
        this.personalRecordRepository = personalRecordRepository;
        this.progressionService = progressionService;
        this.workoutFeedbackRepository = workoutFeedbackRepository;
        this.notificationService = notificationService;
        this.coachClientRepository = coachClientRepository;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    // ---------------------------------------------------------------- today

    /**
     * Resolves which ProgramDay a client should train today by cycling through
     * the active program's days based on calendar days elapsed since start,
     * in the client's own timezone. Programs are not tied to specific weekdays,
     * matching the "Day 1, Day 2, Day 3 (rest)..." style laid out in the spec.
     */
    @Transactional(readOnly = true)
    public Optional<ProgramDay> resolveTodayProgramDay(UUID clientId, String clientTimezone) {
        return resolveProgramDayFor(clientId, clientTimezone, LocalDate.now(safeZone(clientTimezone)));
    }

    /**
     * The same cycle walk for an arbitrary date, less any skips earlier in that
     * date's Monday-Sunday week (see {@link #skipsEarlierInWeek}). Skipping
     * Monday therefore makes Monday's session reappear on Tuesday, Tuesday's on
     * Wednesday, and so on.
     */
    @Transactional(readOnly = true)
    public Optional<ProgramDay> resolveProgramDayFor(UUID clientId, String clientTimezone, LocalDate date) {
        Optional<ClientProgram> activeOpt = clientProgramRepository
                .findFirstByClientIdAndStatusOrderByStartDateDesc(clientId, ClientProgramStatus.ACTIVE);
        if (activeOpt.isEmpty()) {
            return Optional.empty();
        }
        ClientProgram active = activeOpt.get();
        List<ProgramDay> days = programDayRepository.findByProgramIdOrderByDayNumberAsc(active.getProgramId());
        return cycleDay(active, days, date, skipsEarlierInWeek(clientId, clientTimezone, date));
    }

    /**
     * The cycle arithmetic on its own, with no I/O: which day of the program a
     * date lands on, given how many skips have already pushed the week back.
     * Both the single-date lookup above and the week strip go through this, so
     * the two can never disagree about what a date holds.
     */
    static Optional<ProgramDay> cycleDay(ClientProgram active, List<ProgramDay> days, LocalDate date, long skips) {
        if (active == null || days.isEmpty()) {
            return Optional.empty();
        }
        long daysSinceStart = ChronoUnit.DAYS.between(active.getStartDate(), date);
        if (daysSinceStart < 0) {
            return Optional.empty();
        }
        long shifted = daysSinceStart - skips;
        if (shifted < 0) {
            return Optional.empty();
        }
        return Optional.of(days.get((int) (shifted % days.size())));
    }

    /**
     * How many days the schedule has slid back by, for this date. Deliberately
     * scoped to the containing Monday-Sunday week: every Monday the schedule
     * snaps back to whatever the untouched cycle prescribes, so a skip late in
     * the week pushes a session off the end of that week rather than bleeding
     * into the next one. The count is derived from the SKIPPED sessions
     * themselves, so there is no separate offset column to keep in sync.
     *
     * <p>Adherence deliberately does <em>not</em> apply this shift: what the
     * coach planned for the week is what they planned, and a skipped session
     * stays counted as missed. See docs/fitness-calculations.md.
     */
    private long skipsEarlierInWeek(UUID clientId, String clientTimezone, LocalDate date) {
        ZoneId zone = safeZone(clientTimezone);
        LocalDate weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Instant from = weekStart.atStartOfDay(zone).toInstant();
        Instant to = date.atStartOfDay(zone).toInstant();
        if (!from.isBefore(to)) {
            return 0;
        }
        return sessionRepository.countByClientIdAndStatusAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                clientId, WorkoutSessionStatus.SKIPPED, from, to);
    }

    private ZoneId safeZone(String tz) {
        try {
            return tz == null ? ZoneId.of("UTC") : ZoneId.of(tz);
        } catch (Exception e) {
            return ZoneId.of("UTC");
        }
    }

    @Transactional
    public WorkoutSession getOrCreateTodaySession(UUID clientId, String clientTimezone) {
        ZoneId zone = safeZone(clientTimezone);
        Instant startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant();
        Instant endOfDay = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant();

        Optional<WorkoutSession> existing = sessionRepository
                .findByClientIdAndStartedAtBetween(clientId, startOfDay, endOfDay)
                .stream().max(Comparator.comparing(WorkoutSession::getStartedAt));
        if (existing.isPresent()) {
            return existing.get();
        }

        ProgramDay programDay = resolveTodayProgramDay(clientId, clientTimezone).orElse(null);
        WorkoutSession session = new WorkoutSession(clientId, programDay == null ? null : programDay.getId());
        // This runs whenever the client merely looks at today's plan, so it must
        // not look like training: the constructor's IN_PROGRESS is right for an
        // explicitly started workout, not for this. logSet() promotes it to
        // IN_PROGRESS on the first set, which is what the coach's "training
        // today" count relies on.
        session.setStatus(WorkoutSessionStatus.NOT_STARTED);
        return sessionRepository.save(session);
    }

    // ----------------------------------------------------------------- week

    /**
     * The client's Monday-Sunday week, already shifted for any skips, so the
     * strip shows where each session actually landed rather than where the
     * untouched cycle would have put it.
     */
    @Transactional(readOnly = true)
    public List<WeekDayDto> weekSchedule(UUID clientId, String clientTimezone) {
        ZoneId zone = safeZone(clientTimezone);
        LocalDate today = LocalDate.now(zone);
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Instant weekStartInstant = weekStart.atStartOfDay(zone).toInstant();

        // Everything the seven days need, loaded once: the week's sessions, the
        // active program, its days, and which of those days prescribe work.
        // The per-day loop below is then arithmetic over these, not queries.
        List<WorkoutSession> weekSessions = sessionRepository.findByClientIdAndStartedAtBetween(
                clientId, weekStartInstant, weekStart.plusDays(7).atStartOfDay(zone).toInstant());
        ClientProgram active = clientProgramRepository
                .findFirstByClientIdAndStatusOrderByStartDateDesc(clientId, ClientProgramStatus.ACTIVE)
                .orElse(null);
        List<ProgramDay> days = active == null ? List.of()
                : programDayRepository.findByProgramIdOrderByDayNumberAsc(active.getProgramId());
        java.util.Set<UUID> daysWithExercises = days.isEmpty() ? java.util.Set.of()
                : programExerciseRepository
                        .findByProgramDayIdInOrderByOrderIndexAsc(days.stream().map(ProgramDay::getId).toList())
                        .stream().map(ProgramExercise::getProgramDayId).collect(Collectors.toSet());

        List<WeekDayDto> week = new java.util.ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);
            Instant dayStart = date.atStartOfDay(zone).toInstant();
            // Same half-open [Monday, this day) window skipsEarlierInWeek counts.
            long skipsBefore = weekSessions.stream()
                    .filter(s -> s.getStatus() == WorkoutSessionStatus.SKIPPED)
                    .filter(s -> !s.getStartedAt().isBefore(weekStartInstant) && s.getStartedAt().isBefore(dayStart))
                    .count();
            ProgramDay day = cycleDay(active, days, date, skipsBefore).orElse(null);
            boolean restDay = day == null || !daysWithExercises.contains(day.getId());

            WorkoutSession session = weekSessions.stream()
                    .filter(s -> LocalDate.ofInstant(s.getStartedAt(), zone).equals(date))
                    .max(Comparator.comparing(WorkoutSession::getStartedAt))
                    .orElse(null);

            week.add(new WeekDayDto(
                    date,
                    date.getDayOfWeek().name(),
                    date.equals(today),
                    date.isBefore(today),
                    day == null ? null : day.getId(),
                    day == null ? null : day.getName(),
                    restDay,
                    session == null ? null : session.getId(),
                    session == null ? null : session.getStatus(),
                    session == null ? null : session.getSkipReason(),
                    skipsBefore > 0));
        }
        return week;
    }

    /**
     * Marks today's session SKIPPED with the client's reason and tells their
     * coach. Everything left in the week slides down a day as a consequence -
     * that falls out of {@link #skipsEarlierInWeek} rather than being written
     * anywhere, so there is no stored schedule that can drift out of sync.
     */
    @Transactional
    public WorkoutSession skipToday(UUID clientId, String clientTimezone, String reason) {
        WorkoutSession session = getOrCreateTodaySession(clientId, clientTimezone);
        if (session.getStatus() == WorkoutSessionStatus.COMPLETED) {
            throw new ConflictException("This workout is already completed and can't be skipped.");
        }
        if (session.getStatus() == WorkoutSessionStatus.SKIPPED) {
            throw new ConflictException("This workout has already been skipped.");
        }
        session.markSkipped(reason);
        session = sessionRepository.save(session);

        notifyCoachOfSkip(clientId, session, reason);
        auditService.record(clientId, AuditAction.WORKOUT_SKIPPED, "WorkoutSession", session.getId());
        return session;
    }

    private void notifyCoachOfSkip(UUID clientId, WorkoutSession session, String reason) {
        coachClientRepository.findFirstByClientIdAndStatus(clientId, CoachClientStatus.ACTIVE)
                .ifPresent(link -> {
                    String name = userRepository.findById(clientId)
                            .map(u -> u.getFirstName() + " " + u.getLastName())
                            .orElse("A client");
                    String dayName = session.getProgramDayId() == null ? "their workout"
                            : programDayRepository.findById(session.getProgramDayId())
                                    .map(ProgramDay::getName)
                                    .orElse("their workout");
                    notificationService.notify(
                            link.getCoachId(),
                            NotificationType.WORKOUT_SKIPPED,
                            name + " skipped " + dayName,
                            reason,
                            "WorkoutSession",
                            session.getId());
                });
    }

    @Transactional
    public WorkoutSession startWorkout(UUID clientId, UUID requestedProgramDayId) {
        WorkoutSession session = new WorkoutSession(clientId, requestedProgramDayId);
        session = sessionRepository.save(session);
        auditService.record(clientId, AuditAction.WORKOUT_CREATED, "WorkoutSession", session.getId());
        return session;
    }

    // ------------------------------------------------------------- assembly

    @Transactional(readOnly = true)
    public WorkoutSessionDto toDto(WorkoutSession session) {
        authorizationService.assertCanAccessClient(session.getClientId());

        ProgramDay day = session.getProgramDayId() == null ? null
                : programDayRepository.findById(session.getProgramDayId()).orElse(null);
        List<ProgramExercise> prescribed = day == null ? List.of()
                : programExerciseRepository.findByProgramDayIdOrderByOrderIndexAsc(day.getId());

        List<WorkoutExercise> loggedExercises = workoutExerciseRepository
                .findByWorkoutSessionIdOrderByOrderIndexAsc(session.getId());
        Map<UUID, WorkoutExercise> loggedByExerciseId = loggedExercises.stream()
                .collect(Collectors.toMap(WorkoutExercise::getExerciseId, we -> we, (a, b) -> a));

        Map<UUID, List<WorkoutSet>> setsByWorkoutExercise = loggedExercises.isEmpty() ? Map.of()
                : workoutSetRepository.findByWorkoutExerciseIdInOrderBySetNumberAsc(
                                loggedExercises.stream().map(WorkoutExercise::getId).toList())
                        .stream().collect(Collectors.groupingBy(WorkoutSet::getWorkoutExerciseId));

        List<UUID> exerciseIds = prescribed.isEmpty()
                ? loggedExercises.stream().map(WorkoutExercise::getExerciseId).distinct().toList()
                : prescribed.stream().map(ProgramExercise::getExerciseId).distinct().toList();
        Map<UUID, String> exerciseNames = exerciseCatalog.namesById();

        Map<UUID, PreviousPerformanceDto> previousByExercise =
                previousPerformance(session.getClientId(), exerciseIds, session.getId());

        List<WorkoutExerciseDto> exerciseDtos;
        if (!prescribed.isEmpty()) {
            exerciseDtos = prescribed.stream().map(pe -> buildExerciseDto(
                    pe.getExerciseId(), exerciseNames.get(pe.getExerciseId()), pe.getOrderIndex(),
                    pe.getSets(), pe.getTargetReps(), pe.getTargetWeight(), pe.getTargetRpe(), pe.getRestSeconds(),
                    pe.getProgressionStrategy(), loggedByExerciseId, setsByWorkoutExercise,
                    previousByExercise)).toList();
        } else {
            exerciseDtos = loggedExercises.stream().map(we -> buildExerciseDto(
                    we.getExerciseId(), exerciseNames.get(we.getExerciseId()), we.getOrderIndex(),
                    null, null, null, null, null, null, loggedByExerciseId, setsByWorkoutExercise,
                    previousByExercise)).toList();
        }

        List<WorkoutSet> allSets = setsByWorkoutExercise.values().stream().flatMap(List::stream).toList();
        BigDecimal totalVolume = progressionService.totalVolume(allSets);

        List<WorkoutFeedbackDto> feedback = workoutFeedbackRepository
                .findByWorkoutSessionIdOrderByCreatedAtDesc(session.getId()).stream()
                .map(f -> new WorkoutFeedbackDto(f.getId(), f.getContent(), f.getCreatedAt()))
                .toList();

        return new WorkoutSessionDto(
                session.getId(), session.getProgramDayId(),
                day == null ? "Workout" : day.getName(), session.getStatus(),
                session.getStartedAt(), session.getCompletedAt(), session.getDurationSeconds(),
                session.getNotes(), session.getOverallRpe(), session.getEnergyLevel(),
                totalVolume, countPrsForSession(session), exerciseDtos, feedback, session.getSkipReason());
    }

    private WorkoutExerciseDto buildExerciseDto(UUID exerciseId, String exerciseName,
                                                 int orderIndex, Integer targetSets, Integer targetReps,
                                                 BigDecimal targetWeight, BigDecimal targetRpe, Integer restSeconds,
                                                 com.fitnesshub.program.ProgressionStrategy strategy,
                                                 Map<UUID, WorkoutExercise> loggedByExerciseId,
                                                 Map<UUID, List<WorkoutSet>> setsByWorkoutExercise,
                                                 Map<UUID, PreviousPerformanceDto> previousByExercise) {
        WorkoutExercise logged = loggedByExerciseId.get(exerciseId);
        List<WorkoutSet> completedSets = logged == null ? List.of()
                : setsByWorkoutExercise.getOrDefault(logged.getId(), List.of());
        return new WorkoutExerciseDto(
                logged == null ? null : logged.getId(), exerciseId, exerciseName, orderIndex,
                targetSets, targetReps, targetWeight, targetRpe, restSeconds, strategy,
                previousByExercise.get(exerciseId),
                completedSets.stream().map(this::toSetDto).toList());
    }

    /**
     * "Last time" for a batch of exercises in a single query: for each one,
     * the best valid set from the most recent completed session containing it
     * (see {@link WorkoutSetRepository#findPreviousBestSets}). Exercises the
     * client has never completed are simply absent from the map.
     *
     * <p>This used to walk the client's last 20 sessions once per exercise,
     * two queries a session. It is now one query however long the history or
     * the workout, and no longer stops looking after 20 sessions.
     */
    @Transactional(readOnly = true)
    public Map<UUID, PreviousPerformanceDto> previousPerformance(UUID clientId, List<UUID> exerciseIds,
                                                                 UUID excludeSessionId) {
        if (exerciseIds.isEmpty()) {
            return Map.of();
        }
        // A session that has never been saved has no id; nothing can match it, but SQL needs a value.
        UUID exclude = excludeSessionId == null ? new UUID(0L, 0L) : excludeSessionId;
        Map<UUID, PreviousPerformanceDto> byExercise = new java.util.HashMap<>();
        for (Object[] row : workoutSetRepository.findPreviousBestSets(clientId, exclude, exerciseIds)) {
            byExercise.put((UUID) row[0], new PreviousPerformanceDto(
                    toInstant(row[1]), (BigDecimal) row[2], ((Number) row[3]).intValue(), (BigDecimal) row[4]));
        }
        return byExercise;
    }

    @Transactional(readOnly = true)
    public PreviousPerformanceDto findPreviousPerformance(UUID clientId, UUID exerciseId, UUID excludeSessionId) {
        return previousPerformance(clientId, List.of(exerciseId), excludeSessionId).get(exerciseId);
    }

    /** Native queries hand timestamps back in whichever java.time or java.sql type the driver chose. */
    private static Instant toInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof java.time.OffsetDateTime offset) {
            return offset.toInstant();
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        throw new IllegalStateException("Unexpected timestamp type: " + (value == null ? "null" : value.getClass()));
    }

    private WorkoutSetDto toSetDto(WorkoutSet s) {
        return new WorkoutSetDto(s.getId(), s.getSetNumber(), s.getReps(), s.getWeight(), s.getRpe(), s.getRir(),
                s.isWarmup(), s.isFailure(), s.getCompletedAt());
    }

    // -------------------------------------------------------------- logging

    @Transactional
    public SetCompletionResult logSet(UUID sessionId, LogSetRequest request) {
        WorkoutSession session = getSessionOwnedByCallingClient(sessionId);
        if (session.getStatus() == WorkoutSessionStatus.COMPLETED) {
            throw new ConflictException("This workout is already completed; sets can no longer be logged.");
        }
        exerciseRepository.findById(request.exerciseId())
                .orElseThrow(() -> NotFoundException.of("Exercise", request.exerciseId()));

        WorkoutExercise workoutExercise = workoutExerciseRepository
                .findByWorkoutSessionIdAndExerciseId(sessionId, request.exerciseId())
                .orElseGet(() -> {
                    int nextOrder = workoutExerciseRepository
                            .findByWorkoutSessionIdOrderByOrderIndexAsc(sessionId).size();
                    return workoutExerciseRepository.save(
                            new WorkoutExercise(sessionId, request.exerciseId(), nextOrder));
                });

        int nextSetNumber = workoutSetRepository
                .findByWorkoutExerciseIdOrderBySetNumberAsc(workoutExercise.getId()).size() + 1;

        WorkoutSet set = new WorkoutSet(workoutExercise.getId(), nextSetNumber, request.reps(), request.weight());
        set.setRpe(request.rpe());
        set.setRir(request.rir());
        set.setWarmup(request.isWarmup());
        set.setFailure(request.isFailure());
        set = workoutSetRepository.save(set);

        if (session.getStatus() == WorkoutSessionStatus.NOT_STARTED) {
            session.setStatus(WorkoutSessionStatus.IN_PROGRESS);
            sessionRepository.save(session);
        }

        List<PersonalRecord> newPrs = personalRecordService
                .checkSetForRecords(session.getClientId(), request.exerciseId(), set);

        return new SetCompletionResult(toSetDto(set),
                newPrs.stream().map(SetCompletionResult.PersonalRecordDto::from).toList());
    }

    @Transactional
    public WorkoutSetDto updateSet(UUID setId, UpdateSetRequest request) {
        WorkoutSet set = workoutSetRepository.findById(setId)
                .orElseThrow(() -> NotFoundException.of("WorkoutSet", setId));
        WorkoutExercise workoutExercise = workoutExerciseRepository.findById(set.getWorkoutExerciseId())
                .orElseThrow(() -> NotFoundException.of("WorkoutExercise", set.getWorkoutExerciseId()));
        WorkoutSession session = getSessionOwnedByCallingClient(workoutExercise.getWorkoutSessionId());

        if (session.getStatus() == WorkoutSessionStatus.COMPLETED) {
            throw new ConflictException("This workout is completed; sets are immutable. Contact your coach for corrections.");
        }

        if (request.reps() != null) set.setReps(request.reps());
        if (request.weight() != null) set.setWeight(request.weight());
        if (request.rpe() != null) set.setRpe(request.rpe());
        if (request.rir() != null) set.setRir(request.rir());
        if (request.isFailure() != null) set.setFailure(request.isFailure());

        return toSetDto(workoutSetRepository.save(set));
    }

    @Transactional
    public WorkoutSessionDto completeWorkout(UUID sessionId, CompleteWorkoutRequest request) {
        WorkoutSession session = getSessionOwnedByCallingClient(sessionId);
        if (session.getStatus() == WorkoutSessionStatus.COMPLETED) {
            throw new ConflictException("This workout is already completed.");
        }

        session.setCompletedAt(Instant.now());
        session.setDurationSeconds((int) Duration.between(session.getStartedAt(), session.getCompletedAt()).getSeconds());
        session.setNotes(request.notes());
        session.setOverallRpe(request.overallRpe());
        session.setEnergyLevel(request.energyLevel());
        session.setStatus(WorkoutSessionStatus.COMPLETED);
        sessionRepository.save(session);

        for (WorkoutExercise we : workoutExerciseRepository.findByWorkoutSessionIdOrderByOrderIndexAsc(sessionId)) {
            List<WorkoutSet> sets = workoutSetRepository.findByWorkoutExerciseIdOrderBySetNumberAsc(we.getId());
            if (sets.isEmpty()) {
                continue;
            }
            UUID representativeSetId = sets.get(sets.size() - 1).getId();
            personalRecordService.checkSessionVolumeForRecord(
                    session.getClientId(), we.getExerciseId(), sets, representativeSetId, session.getCompletedAt());
        }

        auditService.record(session.getClientId(), AuditAction.WORKOUT_COMPLETED, "WorkoutSession", session.getId());
        return toDto(session);
    }

    @Transactional
    public WorkoutFeedbackDto leaveFeedback(UUID sessionId, String content) {
        WorkoutSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> NotFoundException.of("WorkoutSession", sessionId));
        authorizationService.assertCoachOwnsClient(currentUser.id(), session.getClientId());
        WorkoutFeedback feedback = workoutFeedbackRepository.save(
                new WorkoutFeedback(sessionId, currentUser.id(), content));
        notificationService.notify(session.getClientId(), com.fitnesshub.notification.NotificationType.COACH_FEEDBACK,
                "Your coach left feedback", content, "WorkoutSession", sessionId);
        return new WorkoutFeedbackDto(feedback.getId(), feedback.getContent(), feedback.getCreatedAt());
    }

    // --------------------------------------------------------------- reads

    @Transactional(readOnly = true)
    public Page<WorkoutSession> history(UUID clientId, Pageable pageable) {
        authorizationService.assertCanAccessClient(clientId);
        return sessionRepository.findByClientIdOrderByStartedAtDesc(clientId, pageable);
    }

    @Transactional(readOnly = true)
    public WorkoutSummaryDto toSummaryDto(WorkoutSession session) {
        return toSummaryDtos(List.of(session)).get(session.getId());
    }

    /**
     * Summaries for a page of one client's sessions, keyed by session id.
     *
     * <p>Five queries for the whole page - day names, logged exercises, sets,
     * and personal records - where building each row on its own cost four
     * queries per session (80 for a default 20-row page).
     */
    @Transactional(readOnly = true)
    public Map<UUID, WorkoutSummaryDto> toSummaryDtos(List<WorkoutSession> sessions) {
        if (sessions.isEmpty()) {
            return Map.of();
        }
        List<UUID> sessionIds = sessions.stream().map(WorkoutSession::getId).toList();

        Map<UUID, String> dayNames = programDayRepository.findAllById(
                        sessions.stream().map(WorkoutSession::getProgramDayId)
                                .filter(java.util.Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(ProgramDay::getId, ProgramDay::getName));

        List<WorkoutExercise> exercises = workoutExerciseRepository
                .findByWorkoutSessionIdInOrderByOrderIndexAsc(sessionIds);
        Map<UUID, UUID> sessionByWorkoutExercise = exercises.stream()
                .collect(Collectors.toMap(WorkoutExercise::getId, WorkoutExercise::getWorkoutSessionId));
        Map<UUID, List<WorkoutSet>> setsBySession = exercises.isEmpty() ? Map.of()
                : workoutSetRepository.findByWorkoutExerciseIdInOrderBySetNumberAsc(
                                exercises.stream().map(WorkoutExercise::getId).toList())
                        .stream().collect(Collectors.groupingBy(
                                set -> sessionByWorkoutExercise.get(set.getWorkoutExerciseId())));

        Map<UUID, Integer> prCounts = countPrsForSessions(sessions);

        Map<UUID, WorkoutSummaryDto> summaries = new java.util.HashMap<>();
        for (WorkoutSession session : sessions) {
            List<WorkoutSet> sets = setsBySession.getOrDefault(session.getId(), List.of());
            summaries.put(session.getId(), new WorkoutSummaryDto(
                    session.getId(),
                    session.getProgramDayId() == null ? "Workout"
                            : dayNames.getOrDefault(session.getProgramDayId(), "Workout"),
                    session.getStartedAt(), session.getDurationSeconds(),
                    progressionService.totalVolume(sets), progressionService.averageRpe(sets),
                    session.getStatus(), prCounts.getOrDefault(session.getId(), 0), session.getSkipReason()));
        }
        return summaries;
    }

    /**
     * PRs achieved during each session, from one query per client covering the
     * span of the whole page; each record is then matched to the session whose
     * start-to-finish window (padded a second either side) contains it.
     */
    private Map<UUID, Integer> countPrsForSessions(List<WorkoutSession> sessions) {
        Map<UUID, Integer> counts = new java.util.HashMap<>();
        Map<UUID, List<WorkoutSession>> finishedByClient = sessions.stream()
                .filter(s -> s.getCompletedAt() != null)
                .collect(Collectors.groupingBy(WorkoutSession::getClientId));
        finishedByClient.forEach((clientId, finished) -> {
            Instant earliest = finished.stream().map(WorkoutSession::getStartedAt)
                    .min(Comparator.naturalOrder()).orElseThrow().minusSeconds(1);
            Instant latest = finished.stream().map(WorkoutSession::getCompletedAt)
                    .max(Comparator.naturalOrder()).orElseThrow().plusSeconds(1);
            List<PersonalRecord> records = personalRecordRepository
                    .findByClientIdAndAchievedAtBetween(clientId, earliest, latest);
            for (WorkoutSession session : finished) {
                Instant windowStart = session.getStartedAt().minusSeconds(1);
                Instant windowEnd = session.getCompletedAt().plusSeconds(1);
                int count = (int) records.stream()
                        .filter(r -> !r.getAchievedAt().isBefore(windowStart) && !r.getAchievedAt().isAfter(windowEnd))
                        .count();
                counts.put(session.getId(), count);
            }
        });
        return counts;
    }

    private int countPrsForSession(WorkoutSession session) {
        if (session.getCompletedAt() == null) {
            return 0;
        }
        Instant windowStart = session.getStartedAt().minusSeconds(1);
        Instant windowEnd = session.getCompletedAt().plusSeconds(1);
        return personalRecordRepository
                .findByClientIdAndAchievedAtBetween(session.getClientId(), windowStart, windowEnd)
                .size();
    }

    @Transactional(readOnly = true)
    public WorkoutSession getById(UUID sessionId) {
        WorkoutSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> NotFoundException.of("WorkoutSession", sessionId));
        authorizationService.assertCanAccessClient(session.getClientId());
        return session;
    }

    private WorkoutSession getSessionOwnedByCallingClient(UUID sessionId) {
        WorkoutSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> NotFoundException.of("WorkoutSession", sessionId));
        if (!session.getClientId().equals(currentUser.id())) {
            throw com.fitnesshub.common.exception.ForbiddenException.accessDenied();
        }
        return session;
    }
}

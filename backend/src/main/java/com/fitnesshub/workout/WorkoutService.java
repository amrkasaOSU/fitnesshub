package com.fitnesshub.workout;

import com.fitnesshub.audit.AuditAction;
import com.fitnesshub.audit.AuditService;
import com.fitnesshub.common.exception.ConflictException;
import com.fitnesshub.common.exception.NotFoundException;
import com.fitnesshub.exercise.Exercise;
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
        if (days.isEmpty()) {
            return Optional.empty();
        }
        long daysSinceStart = ChronoUnit.DAYS.between(active.getStartDate(), date);
        if (daysSinceStart < 0) {
            return Optional.empty();
        }
        long shifted = daysSinceStart - skipsEarlierInWeek(clientId, clientTimezone, date);
        if (shifted < 0) {
            return Optional.empty();
        }
        int index = (int) (shifted % days.size());
        return Optional.of(days.get(index));
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

        List<WorkoutSession> weekSessions = sessionRepository.findByClientIdAndStartedAtBetween(
                clientId,
                weekStart.atStartOfDay(zone).toInstant(),
                weekStart.plusDays(7).atStartOfDay(zone).toInstant());

        List<WeekDayDto> week = new java.util.ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);
            ProgramDay day = resolveProgramDayFor(clientId, clientTimezone, date).orElse(null);
            boolean restDay = day == null
                    || programExerciseRepository.findByProgramDayIdOrderByOrderIndexAsc(day.getId()).isEmpty();

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
                    skipsEarlierInWeek(clientId, clientTimezone, date) > 0));
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
        Map<UUID, String> exerciseNames = exerciseRepository.findAllById(exerciseIds).stream()
                .collect(Collectors.toMap(Exercise::getId, Exercise::getName));

        List<WorkoutExerciseDto> exerciseDtos;
        if (!prescribed.isEmpty()) {
            exerciseDtos = prescribed.stream().map(pe -> buildExerciseDto(
                    session, pe.getExerciseId(), exerciseNames.get(pe.getExerciseId()), pe.getOrderIndex(),
                    pe.getSets(), pe.getTargetReps(), pe.getTargetWeight(), pe.getTargetRpe(), pe.getRestSeconds(),
                    pe.getProgressionStrategy(), loggedByExerciseId, setsByWorkoutExercise)).toList();
        } else {
            exerciseDtos = loggedExercises.stream().map(we -> buildExerciseDto(
                    session, we.getExerciseId(), exerciseNames.get(we.getExerciseId()), we.getOrderIndex(),
                    null, null, null, null, null, null, loggedByExerciseId, setsByWorkoutExercise)).toList();
        }

        List<WorkoutSet> allSets = setsByWorkoutExercise.values().stream().flatMap(List::stream).toList();
        BigDecimal totalVolume = progressionService.totalVolume(allSets);

        List<WorkoutFeedbackDto> feedback = workoutFeedbackRepository
                .findByWorkoutSessionIdOrderByCreatedAtDesc(session.getId()).stream()
                .map(f -> new WorkoutFeedbackDto(f.getId(), f.getContent(), f.getCreatedAt()))
                .toList();

        return new WorkoutSessionDto(
                session.getId(), day == null ? "Workout" : day.getName(), session.getStatus(),
                session.getStartedAt(), session.getCompletedAt(), session.getDurationSeconds(),
                session.getNotes(), session.getOverallRpe(), session.getEnergyLevel(),
                totalVolume, countPrsForSession(session), exerciseDtos, feedback, session.getSkipReason());
    }

    private WorkoutExerciseDto buildExerciseDto(WorkoutSession session, UUID exerciseId, String exerciseName,
                                                 int orderIndex, Integer targetSets, Integer targetReps,
                                                 BigDecimal targetWeight, BigDecimal targetRpe, Integer restSeconds,
                                                 com.fitnesshub.program.ProgressionStrategy strategy,
                                                 Map<UUID, WorkoutExercise> loggedByExerciseId,
                                                 Map<UUID, List<WorkoutSet>> setsByWorkoutExercise) {
        WorkoutExercise logged = loggedByExerciseId.get(exerciseId);
        List<WorkoutSet> completedSets = logged == null ? List.of()
                : setsByWorkoutExercise.getOrDefault(logged.getId(), List.of());

        PreviousPerformanceDto previous = findPreviousPerformance(session.getClientId(), exerciseId, session.getId());

        return new WorkoutExerciseDto(
                logged == null ? null : logged.getId(), exerciseId, exerciseName, orderIndex,
                targetSets, targetReps, targetWeight, targetRpe, restSeconds, strategy, previous,
                completedSets.stream().map(this::toSetDto).toList());
    }

    @Transactional(readOnly = true)
    public PreviousPerformanceDto findPreviousPerformance(UUID clientId, UUID exerciseId, UUID excludeSessionId) {
        List<WorkoutSession> recentSessions = sessionRepository.findCompletedForClient(clientId).stream()
                .filter(s -> !s.getId().equals(excludeSessionId))
                .limit(20)
                .toList();
        for (WorkoutSession session : recentSessions) {
            Optional<WorkoutExercise> we = workoutExerciseRepository
                    .findByWorkoutSessionIdAndExerciseId(session.getId(), exerciseId);
            if (we.isEmpty()) {
                continue;
            }
            List<WorkoutSet> sets = workoutSetRepository
                    .findByWorkoutExerciseIdOrderBySetNumberAsc(we.get().getId()).stream()
                    .filter(WorkoutSet::isValidForOneRepMaxAndPr)
                    .toList();
            if (sets.isEmpty()) {
                continue;
            }
            WorkoutSet best = sets.stream()
                    .max(Comparator.comparing(WorkoutSet::getWeight).thenComparing(WorkoutSet::getReps))
                    .orElseThrow();
            return new PreviousPerformanceDto(best.getCompletedAt(), best.getWeight(), best.getReps(), best.getRpe());
        }
        return null;
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
        ProgramDay day = session.getProgramDayId() == null ? null
                : programDayRepository.findById(session.getProgramDayId()).orElse(null);
        List<WorkoutExercise> exercises = workoutExerciseRepository
                .findByWorkoutSessionIdOrderByOrderIndexAsc(session.getId());
        List<WorkoutSet> sets = exercises.isEmpty() ? List.of()
                : workoutSetRepository.findByWorkoutExerciseIdInOrderBySetNumberAsc(
                        exercises.stream().map(WorkoutExercise::getId).toList());
        BigDecimal volume = progressionService.totalVolume(sets);
        BigDecimal avgRpe = progressionService.averageRpe(sets);
        return new WorkoutSummaryDto(session.getId(), day == null ? "Workout" : day.getName(),
                session.getStartedAt(), session.getDurationSeconds(), volume, avgRpe, session.getStatus(),
                countPrsForSession(session), session.getSkipReason());
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

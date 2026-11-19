package com.fitnesshub.analytics;

import com.fitnesshub.analytics.dto.AdherenceResult;
import com.fitnesshub.program.ClientProgram;
import com.fitnesshub.program.ClientProgramRepository;
import com.fitnesshub.program.ClientProgramStatus;
import com.fitnesshub.program.ProgramDay;
import com.fitnesshub.program.ProgramDayRepository;
import com.fitnesshub.program.ProgramExercise;
import com.fitnesshub.program.ProgramExerciseRepository;
import com.fitnesshub.workout.WorkoutSessionRepository;
import com.fitnesshub.workout.WorkoutSessionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Adherence = completed workouts / workouts the active program actually
 * scheduled in the window (rest days and days with no prescribed exercises
 * never count as "planned", so a client is never penalized for a day their
 * program didn't ask anything of them). See docs/fitness-calculations.md.
 */
@Service
public class AdherenceService {

    private final ClientProgramRepository clientProgramRepository;
    private final ProgramDayRepository programDayRepository;
    private final ProgramExerciseRepository programExerciseRepository;
    private final WorkoutSessionRepository workoutSessionRepository;

    public AdherenceService(ClientProgramRepository clientProgramRepository,
                             ProgramDayRepository programDayRepository,
                             ProgramExerciseRepository programExerciseRepository,
                             WorkoutSessionRepository workoutSessionRepository) {
        this.clientProgramRepository = clientProgramRepository;
        this.programDayRepository = programDayRepository;
        this.programExerciseRepository = programExerciseRepository;
        this.workoutSessionRepository = workoutSessionRepository;
    }

    /** Pure calculation, independently unit-tested without touching the database. */
    public AdherenceResult calculate(LocalDate programStartDate, List<Boolean> dayHasExerciseCycle,
                                      LocalDate from, LocalDate to, int completedWorkouts) {
        if (dayHasExerciseCycle.isEmpty()) {
            return AdherenceResult.noActiveProgram(completedWorkouts);
        }
        LocalDate windowStart = from.isBefore(programStartDate) ? programStartDate : from;
        if (windowStart.isAfter(to)) {
            return AdherenceResult.noActiveProgram(completedWorkouts);
        }

        int planned = 0;
        int cycleLength = dayHasExerciseCycle.size();
        for (LocalDate d = windowStart; !d.isAfter(to); d = d.plusDays(1)) {
            long daysSinceStart = ChronoUnit.DAYS.between(programStartDate, d);
            int index = (int) (daysSinceStart % cycleLength);
            if (dayHasExerciseCycle.get(index)) {
                planned++;
            }
        }

        if (planned == 0) {
            return AdherenceResult.noActiveProgram(completedWorkouts);
        }
        BigDecimal percentage = BigDecimal.valueOf(Math.min(completedWorkouts, planned))
                .divide(BigDecimal.valueOf(planned), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
        return new AdherenceResult(completedWorkouts, planned, percentage);
    }

    @Transactional(readOnly = true)
    public AdherenceResult calculateForClient(UUID clientId, LocalDate from, LocalDate to, ZoneId zone) {
        Optional<ClientProgram> activeOpt = clientProgramRepository
                .findFirstByClientIdAndStatusOrderByStartDateDesc(clientId, ClientProgramStatus.ACTIVE);

        Instant windowStart = from.atStartOfDay(zone).toInstant();
        Instant windowEnd = to.plusDays(1).atStartOfDay(zone).toInstant();
        int completed = (int) workoutSessionRepository.countByClientIdAndStatusAndStartedAtBetween(
                clientId, WorkoutSessionStatus.COMPLETED, windowStart, windowEnd);

        if (activeOpt.isEmpty()) {
            return AdherenceResult.noActiveProgram(completed);
        }
        ClientProgram active = activeOpt.get();
        return calculate(active.getStartDate(), cycleFor(active.getProgramId()), from, to, completed);
    }

    /**
     * Which days of a program's cycle prescribe work, in day-number order.
     * Two queries for the whole program rather than one per day.
     */
    private List<Boolean> cycleFor(UUID programId) {
        List<ProgramDay> days = programDayRepository.findByProgramIdOrderByDayNumberAsc(programId);
        if (days.isEmpty()) {
            return List.of();
        }
        Set<UUID> daysWithExercises = programExerciseRepository
                .findByProgramDayIdInOrderByOrderIndexAsc(days.stream().map(ProgramDay::getId).toList())
                .stream().map(ProgramExercise::getProgramDayId).collect(Collectors.toSet());
        return days.stream().map(day -> daysWithExercises.contains(day.getId())).toList();
    }

    /**
     * The same calculation as {@link #calculateForClient}, from rows already
     * loaded into a {@link ClientSnapshot}. The window must fall inside the
     * snapshot's session lookback (the last week, which is all the roster
     * needs); it is not a general-purpose date range.
     */
    public AdherenceResult calculateFromSnapshot(ClientSnapshot snapshot, LocalDate from, LocalDate to, ZoneId zone) {
        Instant windowStart = from.atStartOfDay(zone).toInstant();
        Instant windowEnd = to.plusDays(1).atStartOfDay(zone).toInstant();
        int completed = (int) snapshot.recentSessions().stream()
                .filter(s -> s.getStatus() == WorkoutSessionStatus.COMPLETED)
                .filter(s -> !s.getStartedAt().isBefore(windowStart) && !s.getStartedAt().isAfter(windowEnd))
                .count();
        if (snapshot.activeProgram() == null) {
            return AdherenceResult.noActiveProgram(completed);
        }
        return calculate(snapshot.activeProgram().getStartDate(), snapshot.programCycle(), from, to, completed);
    }
}

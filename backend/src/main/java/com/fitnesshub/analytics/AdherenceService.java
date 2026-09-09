package com.fitnesshub.analytics;

import com.fitnesshub.analytics.dto.AdherenceResult;
import com.fitnesshub.program.ClientProgram;
import com.fitnesshub.program.ClientProgramRepository;
import com.fitnesshub.program.ClientProgramStatus;
import com.fitnesshub.program.ProgramDay;
import com.fitnesshub.program.ProgramDayRepository;
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
import java.util.UUID;

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
        List<ProgramDay> days = programDayRepository.findByProgramIdOrderByDayNumberAsc(active.getProgramId());
        List<Boolean> cycle = days.stream()
                .map(day -> !programExerciseRepository.findByProgramDayIdOrderByOrderIndexAsc(day.getId()).isEmpty())
                .toList();

        return calculate(active.getStartDate(), cycle, from, to, completed);
    }
}

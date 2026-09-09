package com.fitnesshub.analytics;

import com.fitnesshub.analytics.dto.AdherenceResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdherenceServiceTest {

    private final AdherenceService service = new AdherenceService(null, null, null, null);

    @Test
    void calculate_fullyCompliantWeek_is100Percent() {
        // 5-day cycle: Upper, Lower, Rest, Upper, Lower
        List<Boolean> cycle = List.of(true, true, false, true, true);
        LocalDate programStart = LocalDate.of(2026, 8, 3); // Monday

        AdherenceResult result = service.calculate(programStart, cycle,
                programStart, programStart.plusDays(4), 4);

        assertThat(result.plannedWorkouts()).isEqualTo(4);
        assertThat(result.adherencePercentage()).isEqualByComparingTo("100.0");
    }

    @Test
    void calculate_restDaysAreNeverCountedAsPlanned() {
        List<Boolean> cycle = List.of(true, false, false, false, false); // 1 training day, 4 rest days
        LocalDate programStart = LocalDate.of(2026, 8, 3);

        AdherenceResult result = service.calculate(programStart, cycle,
                programStart, programStart.plusDays(4), 1);

        assertThat(result.plannedWorkouts()).isEqualTo(1);
        assertThat(result.adherencePercentage()).isEqualByComparingTo("100.0");
    }

    @Test
    void calculate_lowAdherence_belowTwoThirds() {
        List<Boolean> cycle = List.of(true, true, true); // 3 training days, no rest
        LocalDate programStart = LocalDate.of(2026, 8, 3);

        // 1 of 3 planned workouts completed -> 33.3%
        AdherenceResult result = service.calculate(programStart, cycle,
                programStart, programStart.plusDays(2), 1);

        assertThat(result.adherencePercentage()).isEqualByComparingTo("33.3");
    }

    @Test
    void calculate_extraAdHocWorkouts_capsAt100_neverPenalizesUnscheduledWork() {
        List<Boolean> cycle = List.of(true, false); // 1 training day, 1 rest day
        LocalDate programStart = LocalDate.of(2026, 8, 3);

        // Client trained on both the scheduled day AND the rest day (3 total logged)
        AdherenceResult result = service.calculate(programStart, cycle,
                programStart, programStart.plusDays(1), 3);

        assertThat(result.plannedWorkouts()).isEqualTo(1);
        assertThat(result.adherencePercentage()).isEqualByComparingTo("100.0");
    }

    @Test
    void calculate_noActiveProgram_returnsNullPercentageNotZero() {
        AdherenceResult result = service.calculate(LocalDate.now(), List.of(), LocalDate.now(), LocalDate.now(), 0);
        assertThat(result.plannedWorkouts()).isEqualTo(0);
        assertThat(result.adherencePercentage()).isNull();
    }
}

package com.fitnesshub.progress;

import com.fitnesshub.progress.dto.ExerciseProgressSummary;
import com.fitnesshub.workout.WorkoutSet;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProgressionServiceTest {

    private final ProgressionService service = new ProgressionService();
    private final UUID workoutExerciseId = UUID.randomUUID();

    private WorkoutSet set(int reps, String weight) {
        return new WorkoutSet(workoutExerciseId, 1, reps, new BigDecimal(weight));
    }

    private WorkoutSet warmup(int reps, String weight) {
        WorkoutSet s = set(reps, weight);
        s.setWarmup(true);
        return s;
    }

    private WorkoutSet failed(int reps, String weight) {
        WorkoutSet s = set(reps, weight);
        s.setFailure(true);
        return s;
    }

    // --- Epley 1RM ---

    @Test
    void estimated1Rm_usesEpleyFormula() {
        // 205 lb x 8 reps -> 205 * (1 + 8/30) = 205 * 1.2667 = 259.67
        BigDecimal result = service.estimated1Rm(new BigDecimal("205"), 8);
        assertThat(result).isEqualByComparingTo("259.67");
    }

    @Test
    void estimated1Rm_singleRep_returnsWeightItself() {
        BigDecimal result = service.estimated1Rm(new BigDecimal("225"), 1);
        assertThat(result).isEqualByComparingTo("225.00");
    }

    @Test
    void estimated1Rm_zeroRepsOrNullWeight_returnsZero() {
        assertThat(service.estimated1Rm(new BigDecimal("100"), 0)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(service.estimated1Rm(null, 5)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // --- Volume ---

    @Test
    void totalVolume_sumsWeightTimesRepsAcrossValidSets() {
        List<WorkoutSet> sets = List.of(set(8, "200"), set(6, "210"), set(5, "215"));
        // 200*8 + 210*6 + 215*5 = 1600 + 1260 + 1075 = 3935
        assertThat(service.totalVolume(sets)).isEqualByComparingTo("3935.00");
    }

    @Test
    void totalVolume_excludesWarmupSets() {
        List<WorkoutSet> sets = List.of(warmup(10, "135"), set(8, "200"));
        assertThat(service.totalVolume(sets)).isEqualByComparingTo("1600.00");
    }

    @Test
    void totalVolume_includesFailedSets_becauseRepsWereStillPerformed() {
        List<WorkoutSet> sets = List.of(failed(3, "225"));
        assertThat(service.totalVolume(sets)).isEqualByComparingTo("675.00");
    }

    // --- Best weight / reps / 1RM exclude warmups, failures, zero-rep sets ---

    @Test
    void bestWeight_ignoresWarmupsAndFailures() {
        List<WorkoutSet> sets = List.of(warmup(10, "300"), failed(1, "250"), set(5, "215"));
        assertThat(service.bestWeight(sets)).isEqualByComparingTo("215");
    }

    @Test
    void bestReps_ignoresWarmupsAndFailures() {
        List<WorkoutSet> sets = List.of(warmup(20, "135"), failed(12, "185"), set(8, "205"));
        assertThat(service.bestReps(sets)).isEqualTo(8);
    }

    @Test
    void bestEstimated1RmInSet_picksHighestAmongValidSets() {
        List<WorkoutSet> sets = List.of(set(8, "195"), set(6, "205"), set(5, "215"));
        // 1RMs: 195*1.2667=247.0, 205*1.2=246.0(6reps->1.2), 215*(1+5/30)=250.83
        BigDecimal best = service.bestEstimated1RmInSet(sets);
        assertThat(best).isEqualByComparingTo(service.estimated1Rm(new BigDecimal("215"), 5));
    }

    // --- RPE / failure rate ---

    @Test
    void averageRpe_averagesOnlySetsWithRpeRecorded() {
        WorkoutSet a = set(8, "200");
        a.setRpe(new BigDecimal("8"));
        WorkoutSet b = set(6, "210");
        b.setRpe(new BigDecimal("9"));
        WorkoutSet c = set(5, "215"); // no RPE recorded

        assertThat(service.averageRpe(List.of(a, b, c))).isEqualByComparingTo("8.50");
    }

    @Test
    void failureRate_percentageOfNonWarmupSetsThatFailed() {
        List<WorkoutSet> sets = List.of(warmup(10, "135"), set(8, "200"), failed(3, "225"), failed(2, "225"));
        // 3 non-warmup sets, 2 failed -> 66.67%
        assertThat(service.failureRate(sets)).isEqualByComparingTo("66.67");
    }

    // --- Full summary / trend detection (the data the "should I attempt 225" AI answer is built from) ---

    @Test
    void summarize_stalledBenchScenario_showsDownwardPerformanceTrendAndHighRecentRpe() {
        // Mirrors the seed scenario in section 146 of the spec: reps dropping while weight climbs, RPE creeping to 9.
        Instant week1 = Instant.parse("2026-08-01T00:00:00Z");
        Instant week2 = Instant.parse("2026-08-08T00:00:00Z");
        Instant week3 = Instant.parse("2026-08-15T00:00:00Z");
        Instant week4 = Instant.parse("2026-08-22T00:00:00Z");

        WorkoutSet w1 = set(8, "185"); w1.setRpe(new BigDecimal("7.5"));
        WorkoutSet w2 = set(8, "190"); w2.setRpe(new BigDecimal("8"));
        WorkoutSet w3 = set(7, "195"); w3.setRpe(new BigDecimal("9"));
        WorkoutSet w4 = set(6, "200"); w4.setRpe(new BigDecimal("9"));

        List<ProgressionService.SessionSets> sessions = List.of(
                new ProgressionService.SessionSets(week4, List.of(w4)),
                new ProgressionService.SessionSets(week3, List.of(w3)),
                new ProgressionService.SessionSets(week2, List.of(w2)),
                new ProgressionService.SessionSets(week1, List.of(w1))
        );

        ExerciseProgressSummary summary = service.summarize(sessions);

        assertThat(summary.bestWeight()).isEqualByComparingTo("200");
        assertThat(summary.recentAverageRpe()).isGreaterThanOrEqualTo(new BigDecimal("8.5"));
        // Estimated 1RM week4 (200 x 6 = 240.0) vs week3 (195 x 7 = 240.5) is essentially flat/down,
        // not the runaway improvement the raw weight numbers alone would suggest.
        assertThat(summary.performanceTrend()).isIn("DOWN", "FLAT");
    }

    @Test
    void summarize_noSessions_returnsNoDataMarkers() {
        ExerciseProgressSummary summary = service.summarize(List.of());
        assertThat(summary.volumeTrend()).isEqualTo("NO_DATA");
        assertThat(summary.performanceTrend()).isEqualTo("NO_DATA");
        assertThat(summary.recentSessions()).isEmpty();
    }
}

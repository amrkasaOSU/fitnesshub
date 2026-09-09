package com.fitnesshub.progress;

import com.fitnesshub.progress.dto.ExerciseProgressSummary;
import com.fitnesshub.progress.dto.ExerciseProgressSummary.ExerciseSessionPoint;
import com.fitnesshub.workout.WorkoutSet;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * All fitness-math for progressive overload lives here so it runs identically
 * regardless of which client renders it. See docs/fitness-calculations.md.
 */
@Service
public class ProgressionService {

    private static final int SCALE = 2;
    private static final BigDecimal THIRTY = BigDecimal.valueOf(30);

    /** Epley formula: 1RM = weight * (1 + reps / 30). A single rep returns the weight itself. */
    public BigDecimal estimated1Rm(BigDecimal weight, int reps) {
        if (weight == null || reps <= 0) {
            return BigDecimal.ZERO;
        }
        if (reps == 1) {
            return weight.setScale(SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal repsFactor = BigDecimal.ONE.add(
                BigDecimal.valueOf(reps).divide(THIRTY, 6, RoundingMode.HALF_UP));
        return weight.multiply(repsFactor).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** Sum of weight x reps across sets that counted real work (warmups excluded). */
    public BigDecimal totalVolume(List<WorkoutSet> sets) {
        return sets.stream()
                .filter(WorkoutSet::countsTowardVolume)
                .map(s -> s.getWeight().multiply(BigDecimal.valueOf(s.getReps())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(SCALE, RoundingMode.HALF_UP);
    }

    public BigDecimal bestEstimated1RmInSet(List<WorkoutSet> sets) {
        return sets.stream()
                .filter(WorkoutSet::isValidForOneRepMaxAndPr)
                .map(s -> estimated1Rm(s.getWeight(), s.getReps()))
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);
    }

    public BigDecimal bestWeight(List<WorkoutSet> sets) {
        return sets.stream()
                .filter(WorkoutSet::isValidForOneRepMaxAndPr)
                .map(WorkoutSet::getWeight)
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);
    }

    public int bestReps(List<WorkoutSet> sets) {
        return sets.stream()
                .filter(WorkoutSet::isValidForOneRepMaxAndPr)
                .mapToInt(WorkoutSet::getReps)
                .max()
                .orElse(0);
    }

    public BigDecimal averageRpe(List<WorkoutSet> sets) {
        List<BigDecimal> rpes = sets.stream().map(WorkoutSet::getRpe).filter(r -> r != null).toList();
        if (rpes.isEmpty()) {
            return null;
        }
        BigDecimal sum = rpes.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(rpes.size()), SCALE, RoundingMode.HALF_UP);
    }

    public BigDecimal failureRate(List<WorkoutSet> sets) {
        List<WorkoutSet> working = sets.stream().filter(s -> !s.isWarmup()).toList();
        if (working.isEmpty()) {
            return BigDecimal.ZERO;
        }
        long failed = working.stream().filter(WorkoutSet::isFailure).count();
        return BigDecimal.valueOf(failed)
                .divide(BigDecimal.valueOf(working.size()), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Builds the full progress summary for one exercise from its session history,
     * most recent session first.
     */
    public ExerciseProgressSummary summarize(List<SessionSets> sessionsMostRecentFirst) {
        if (sessionsMostRecentFirst.isEmpty()) {
            return new ExerciseProgressSummary(
                    BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    "NO_DATA", "NO_DATA", null, null, List.of());
        }

        List<WorkoutSet> allSets = sessionsMostRecentFirst.stream().flatMap(s -> s.sets().stream()).toList();
        BigDecimal bestWeight = bestWeight(allSets);
        int bestReps = bestReps(allSets);
        BigDecimal best1Rm = bestEstimated1RmInSet(allSets);

        SessionSets latest = sessionsMostRecentFirst.get(0);
        BigDecimal latestVolume = totalVolume(latest.sets());
        BigDecimal previousVolume = sessionsMostRecentFirst.size() > 1
                ? totalVolume(sessionsMostRecentFirst.get(1).sets())
                : null;

        String volumeTrend = trend(latestVolume, previousVolume);
        String performanceTrend = performanceTrend(sessionsMostRecentFirst);

        List<WorkoutSet> last3SessionsSets = sessionsMostRecentFirst.stream()
                .limit(3).flatMap(s -> s.sets().stream()).toList();
        BigDecimal recentRpe = averageRpe(last3SessionsSets);
        BigDecimal recentFailureRate = failureRate(last3SessionsSets);

        List<ExerciseSessionPoint> points = sessionsMostRecentFirst.stream()
                .limit(10)
                .map(s -> new ExerciseSessionPoint(
                        s.sessionDate(),
                        bestWeight(s.sets()),
                        bestReps(s.sets()),
                        bestEstimated1RmInSet(s.sets()),
                        totalVolume(s.sets()),
                        averageRpe(s.sets())))
                .toList();

        return new ExerciseProgressSummary(bestWeight, bestReps, best1Rm, latestVolume,
                previousVolume == null ? BigDecimal.ZERO : previousVolume, volumeTrend, performanceTrend,
                recentRpe, recentFailureRate, points);
    }

    private String trend(BigDecimal latest, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return "INSUFFICIENT_DATA";
        }
        int cmp = latest.compareTo(previous);
        if (cmp > 0) return "UP";
        if (cmp < 0) return "DOWN";
        return "FLAT";
    }

    private String performanceTrend(List<SessionSets> sessionsMostRecentFirst) {
        if (sessionsMostRecentFirst.size() < 2) {
            return "INSUFFICIENT_DATA";
        }
        BigDecimal latest1Rm = bestEstimated1RmInSet(sessionsMostRecentFirst.get(0).sets());
        BigDecimal prior1Rm = bestEstimated1RmInSet(sessionsMostRecentFirst.get(1).sets());
        return trend(latest1Rm, prior1Rm);
    }

    public record SessionSets(Instant sessionDate, List<WorkoutSet> sets) {
    }
}

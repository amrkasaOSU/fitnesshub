package com.fitnesshub.workout;

import com.fitnesshub.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workout_sets")
public class WorkoutSet extends BaseEntity {

    @Column(name = "workout_exercise_id", nullable = false)
    private UUID workoutExerciseId;

    @Column(name = "set_number", nullable = false)
    private Integer setNumber;

    @Column(nullable = false)
    private Integer reps;

    @Column(nullable = false, precision = 7, scale = 2)
    private BigDecimal weight;

    private BigDecimal rpe;

    private BigDecimal rir;

    @Column(name = "is_warmup", nullable = false)
    private boolean warmup = false;

    @Column(name = "is_failure", nullable = false)
    private boolean failure = false;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    protected WorkoutSet() {
    }

    public WorkoutSet(UUID workoutExerciseId, Integer setNumber, Integer reps, BigDecimal weight) {
        this.workoutExerciseId = workoutExerciseId;
        this.setNumber = setNumber;
        this.reps = reps;
        this.weight = weight;
        this.completedAt = Instant.now();
    }

    /** Counts toward training volume: real reps were performed, whether or not the last rep failed. */
    public boolean countsTowardVolume() {
        return !warmup && reps != null && reps > 0 && weight != null && weight.compareTo(BigDecimal.ZERO) > 0;
    }

    /** Counts toward 1RM estimates and PR detection: excludes warmups, failed reps, and zero-rep entries. */
    public boolean isValidForOneRepMaxAndPr() {
        return countsTowardVolume() && !failure;
    }

    public UUID getWorkoutExerciseId() {
        return workoutExerciseId;
    }

    public Integer getSetNumber() {
        return setNumber;
    }

    public void setSetNumber(Integer setNumber) {
        this.setNumber = setNumber;
    }

    public Integer getReps() {
        return reps;
    }

    public void setReps(Integer reps) {
        this.reps = reps;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public void setWeight(BigDecimal weight) {
        this.weight = weight;
    }

    public BigDecimal getRpe() {
        return rpe;
    }

    public void setRpe(BigDecimal rpe) {
        this.rpe = rpe;
    }

    public BigDecimal getRir() {
        return rir;
    }

    public void setRir(BigDecimal rir) {
        this.rir = rir;
    }

    public boolean isWarmup() {
        return warmup;
    }

    public void setWarmup(boolean warmup) {
        this.warmup = warmup;
    }

    public boolean isFailure() {
        return failure;
    }

    public void setFailure(boolean failure) {
        this.failure = failure;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    /** Only meant for backdating seed/demo data. */
    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}

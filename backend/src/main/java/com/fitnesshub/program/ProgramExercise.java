package com.fitnesshub.program;

import com.fitnesshub.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "program_exercises")
public class ProgramExercise extends BaseEntity {

    @Column(name = "program_day_id", nullable = false)
    private UUID programDayId;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @Column(nullable = false)
    private Integer sets;

    @Column(name = "target_reps", nullable = false)
    private Integer targetReps;

    @Column(name = "target_weight")
    private BigDecimal targetWeight;

    @Column(name = "target_rpe")
    private BigDecimal targetRpe;

    @Column(name = "rest_seconds")
    private Integer restSeconds;

    private String tempo;

    @Column(columnDefinition = "text")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "progression_strategy", nullable = false)
    private ProgressionStrategy progressionStrategy = ProgressionStrategy.DOUBLE_PROGRESSION;

    protected ProgramExercise() {
    }

    public ProgramExercise(UUID programDayId, UUID exerciseId, Integer orderIndex, Integer sets, Integer targetReps) {
        this.programDayId = programDayId;
        this.exerciseId = exerciseId;
        this.orderIndex = orderIndex;
        this.sets = sets;
        this.targetReps = targetReps;
    }

    public UUID getProgramDayId() {
        return programDayId;
    }

    public UUID getExerciseId() {
        return exerciseId;
    }

    public Integer getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(Integer orderIndex) {
        this.orderIndex = orderIndex;
    }

    public Integer getSets() {
        return sets;
    }

    public void setSets(Integer sets) {
        this.sets = sets;
    }

    public Integer getTargetReps() {
        return targetReps;
    }

    public void setTargetReps(Integer targetReps) {
        this.targetReps = targetReps;
    }

    public BigDecimal getTargetWeight() {
        return targetWeight;
    }

    public void setTargetWeight(BigDecimal targetWeight) {
        this.targetWeight = targetWeight;
    }

    public BigDecimal getTargetRpe() {
        return targetRpe;
    }

    public void setTargetRpe(BigDecimal targetRpe) {
        this.targetRpe = targetRpe;
    }

    public Integer getRestSeconds() {
        return restSeconds;
    }

    public void setRestSeconds(Integer restSeconds) {
        this.restSeconds = restSeconds;
    }

    public String getTempo() {
        return tempo;
    }

    public void setTempo(String tempo) {
        this.tempo = tempo;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public ProgressionStrategy getProgressionStrategy() {
        return progressionStrategy;
    }

    public void setProgressionStrategy(ProgressionStrategy progressionStrategy) {
        this.progressionStrategy = progressionStrategy;
    }
}

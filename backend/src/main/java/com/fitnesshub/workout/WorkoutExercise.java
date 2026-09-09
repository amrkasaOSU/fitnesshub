package com.fitnesshub.workout;

import com.fitnesshub.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "workout_exercises")
public class WorkoutExercise extends BaseEntity {

    @Column(name = "workout_session_id", nullable = false)
    private UUID workoutSessionId;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @Column(columnDefinition = "text")
    private String notes;

    protected WorkoutExercise() {
    }

    public WorkoutExercise(UUID workoutSessionId, UUID exerciseId, Integer orderIndex) {
        this.workoutSessionId = workoutSessionId;
        this.exerciseId = exerciseId;
        this.orderIndex = orderIndex;
    }

    public UUID getWorkoutSessionId() {
        return workoutSessionId;
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

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}

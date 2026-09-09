package com.fitnesshub.workout;

import com.fitnesshub.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "workout_feedback")
public class WorkoutFeedback extends BaseEntity {

    @Column(name = "workout_session_id", nullable = false)
    private UUID workoutSessionId;

    @Column(name = "coach_id", nullable = false)
    private UUID coachId;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    protected WorkoutFeedback() {
    }

    public WorkoutFeedback(UUID workoutSessionId, UUID coachId, String content) {
        this.workoutSessionId = workoutSessionId;
        this.coachId = coachId;
        this.content = content;
    }

    public UUID getWorkoutSessionId() {
        return workoutSessionId;
    }

    public UUID getCoachId() {
        return coachId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}

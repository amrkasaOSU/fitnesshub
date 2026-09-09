package com.fitnesshub.progress;

import com.fitnesshub.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "personal_records")
public class PersonalRecord extends BaseEntity {

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "record_type", nullable = false)
    private RecordType recordType;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal value;

    @Column(name = "workout_set_id")
    private UUID workoutSetId;

    @Column(name = "achieved_at", nullable = false)
    private Instant achievedAt;

    protected PersonalRecord() {
    }

    public PersonalRecord(UUID clientId, UUID exerciseId, RecordType recordType, BigDecimal value,
                           UUID workoutSetId, Instant achievedAt) {
        this.clientId = clientId;
        this.exerciseId = exerciseId;
        this.recordType = recordType;
        this.value = value;
        this.workoutSetId = workoutSetId;
        this.achievedAt = achievedAt;
    }

    public UUID getClientId() {
        return clientId;
    }

    public UUID getExerciseId() {
        return exerciseId;
    }

    public RecordType getRecordType() {
        return recordType;
    }

    public BigDecimal getValue() {
        return value;
    }

    public void setValue(BigDecimal value) {
        this.value = value;
    }

    public UUID getWorkoutSetId() {
        return workoutSetId;
    }

    public void setWorkoutSetId(UUID workoutSetId) {
        this.workoutSetId = workoutSetId;
    }

    public Instant getAchievedAt() {
        return achievedAt;
    }

    public void setAchievedAt(Instant achievedAt) {
        this.achievedAt = achievedAt;
    }
}

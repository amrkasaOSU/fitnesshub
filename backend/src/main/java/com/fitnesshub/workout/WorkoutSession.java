package com.fitnesshub.workout;

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
@Table(name = "workout_sessions")
public class WorkoutSession extends BaseEntity {

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "program_day_id")
    private UUID programDayId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "overall_rpe")
    private BigDecimal overallRpe;

    @Column(name = "energy_level")
    private Integer energyLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkoutSessionStatus status = WorkoutSessionStatus.NOT_STARTED;

    protected WorkoutSession() {
    }

    public WorkoutSession(UUID clientId, UUID programDayId) {
        this.clientId = clientId;
        this.programDayId = programDayId;
        this.startedAt = Instant.now();
        this.status = WorkoutSessionStatus.IN_PROGRESS;
    }

    public UUID getClientId() {
        return clientId;
    }

    public UUID getProgramDayId() {
        return programDayId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    /** Only meant for backdating seed/demo data - real sessions get their startedAt from the constructor. */
    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public BigDecimal getOverallRpe() {
        return overallRpe;
    }

    public void setOverallRpe(BigDecimal overallRpe) {
        this.overallRpe = overallRpe;
    }

    public Integer getEnergyLevel() {
        return energyLevel;
    }

    public void setEnergyLevel(Integer energyLevel) {
        this.energyLevel = energyLevel;
    }

    public WorkoutSessionStatus getStatus() {
        return status;
    }

    public void setStatus(WorkoutSessionStatus status) {
        this.status = status;
    }
}

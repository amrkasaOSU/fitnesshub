package com.fitnesshub.program;

import com.fitnesshub.client.FitnessGoal;
import com.fitnesshub.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Immutable-once-assigned. Editing a program that already has a ClientProgram
 * assignment produces a new Program row (version + 1, previousVersionId set)
 * instead of mutating this one, so completed workout history stays attached to
 * the exact program shape a client trained under. See ProgramService#update.
 */
@Entity
@Table(name = "programs")
public class Program extends BaseEntity {

    @Column(name = "coach_id", nullable = false)
    private UUID coachId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "duration_weeks", nullable = false)
    private Integer durationWeeks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FitnessGoal goal;

    @Column(nullable = false)
    private int version = 1;

    @Column(name = "previous_version_id")
    private UUID previousVersionId;

    protected Program() {
    }

    public Program(UUID coachId, String name, Integer durationWeeks, FitnessGoal goal) {
        this.coachId = coachId;
        this.name = name;
        this.durationWeeks = durationWeeks;
        this.goal = goal;
    }

    public UUID getCoachId() {
        return coachId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getDurationWeeks() {
        return durationWeeks;
    }

    public void setDurationWeeks(Integer durationWeeks) {
        this.durationWeeks = durationWeeks;
    }

    public FitnessGoal getGoal() {
        return goal;
    }

    public void setGoal(FitnessGoal goal) {
        this.goal = goal;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public UUID getPreviousVersionId() {
        return previousVersionId;
    }

    public void setPreviousVersionId(UUID previousVersionId) {
        this.previousVersionId = previousVersionId;
    }
}

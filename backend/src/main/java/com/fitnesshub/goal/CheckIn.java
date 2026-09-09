package com.fitnesshub.goal;

import com.fitnesshub.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "check_ins")
public class CheckIn extends BaseEntity {

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "week_start_date", nullable = false)
    private LocalDate weekStartDate;

    @Column(precision = 6, scale = 2)
    private BigDecimal weight;

    @Column(name = "energy_score", nullable = false)
    private Integer energyScore;

    @Column(name = "sleep_score", nullable = false)
    private Integer sleepScore;

    @Column(name = "stress_score", nullable = false)
    private Integer stressScore;

    @Column(name = "hunger_score", nullable = false)
    private Integer hungerScore;

    @Column(name = "workout_adherence", nullable = false)
    private Integer workoutAdherence;

    @Column(name = "nutrition_adherence", nullable = false)
    private Integer nutritionAdherence;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "coach_reviewed_at")
    private Instant coachReviewedAt;

    @Column(name = "coach_response", columnDefinition = "text")
    private String coachResponse;

    protected CheckIn() {
    }

    public CheckIn(UUID clientId, LocalDate weekStartDate) {
        this.clientId = clientId;
        this.weekStartDate = weekStartDate;
        this.submittedAt = Instant.now();
    }

    public UUID getClientId() {
        return clientId;
    }

    public LocalDate getWeekStartDate() {
        return weekStartDate;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public void setWeight(BigDecimal weight) {
        this.weight = weight;
    }

    public Integer getEnergyScore() {
        return energyScore;
    }

    public void setEnergyScore(Integer energyScore) {
        this.energyScore = energyScore;
    }

    public Integer getSleepScore() {
        return sleepScore;
    }

    public void setSleepScore(Integer sleepScore) {
        this.sleepScore = sleepScore;
    }

    public Integer getStressScore() {
        return stressScore;
    }

    public void setStressScore(Integer stressScore) {
        this.stressScore = stressScore;
    }

    public Integer getHungerScore() {
        return hungerScore;
    }

    public void setHungerScore(Integer hungerScore) {
        this.hungerScore = hungerScore;
    }

    public Integer getWorkoutAdherence() {
        return workoutAdherence;
    }

    public void setWorkoutAdherence(Integer workoutAdherence) {
        this.workoutAdherence = workoutAdherence;
    }

    public Integer getNutritionAdherence() {
        return nutritionAdherence;
    }

    public void setNutritionAdherence(Integer nutritionAdherence) {
        this.nutritionAdherence = nutritionAdherence;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getCoachReviewedAt() {
        return coachReviewedAt;
    }

    public void setCoachReviewedAt(Instant coachReviewedAt) {
        this.coachReviewedAt = coachReviewedAt;
    }

    public String getCoachResponse() {
        return coachResponse;
    }

    public void setCoachResponse(String coachResponse) {
        this.coachResponse = coachResponse;
    }
}

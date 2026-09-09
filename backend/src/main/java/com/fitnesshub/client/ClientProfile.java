package com.fitnesshub.client;

import com.fitnesshub.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "client_profiles")
public class ClientProfile extends BaseEntity {

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "coach_id", nullable = false)
    private UUID coachId;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "height_cm")
    private BigDecimal heightCm;

    @Enumerated(EnumType.STRING)
    private Sex sex;

    @Enumerated(EnumType.STRING)
    @Column(name = "fitness_goal", nullable = false)
    private FitnessGoal fitnessGoal = FitnessGoal.GENERAL_FITNESS;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_level", nullable = false)
    private ActivityLevel activityLevel = ActivityLevel.MODERATELY_ACTIVE;

    @Column(name = "target_weight")
    private BigDecimal targetWeight;

    @Column(name = "starting_weight")
    private BigDecimal startingWeight;

    @Column(name = "daily_calorie_target")
    private BigDecimal dailyCalorieTarget;

    @Column(name = "daily_protein_target")
    private BigDecimal dailyProteinTarget;

    @Column(name = "daily_step_target")
    private Integer dailyStepTarget = 10000;

    @Column(name = "unit_system", nullable = false)
    private String unitSystem = "IMPERIAL";

    protected ClientProfile() {
    }

    public ClientProfile(UUID userId, UUID coachId) {
        this.userId = userId;
        this.coachId = coachId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getCoachId() {
        return coachId;
    }

    public void setCoachId(UUID coachId) {
        this.coachId = coachId;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public BigDecimal getHeightCm() {
        return heightCm;
    }

    public void setHeightCm(BigDecimal heightCm) {
        this.heightCm = heightCm;
    }

    public Sex getSex() {
        return sex;
    }

    public void setSex(Sex sex) {
        this.sex = sex;
    }

    public FitnessGoal getFitnessGoal() {
        return fitnessGoal;
    }

    public void setFitnessGoal(FitnessGoal fitnessGoal) {
        this.fitnessGoal = fitnessGoal;
    }

    public ActivityLevel getActivityLevel() {
        return activityLevel;
    }

    public void setActivityLevel(ActivityLevel activityLevel) {
        this.activityLevel = activityLevel;
    }

    public BigDecimal getTargetWeight() {
        return targetWeight;
    }

    public void setTargetWeight(BigDecimal targetWeight) {
        this.targetWeight = targetWeight;
    }

    public BigDecimal getStartingWeight() {
        return startingWeight;
    }

    public void setStartingWeight(BigDecimal startingWeight) {
        this.startingWeight = startingWeight;
    }

    public BigDecimal getDailyCalorieTarget() {
        return dailyCalorieTarget;
    }

    public void setDailyCalorieTarget(BigDecimal dailyCalorieTarget) {
        this.dailyCalorieTarget = dailyCalorieTarget;
    }

    public BigDecimal getDailyProteinTarget() {
        return dailyProteinTarget;
    }

    public void setDailyProteinTarget(BigDecimal dailyProteinTarget) {
        this.dailyProteinTarget = dailyProteinTarget;
    }

    public Integer getDailyStepTarget() {
        return dailyStepTarget;
    }

    public void setDailyStepTarget(Integer dailyStepTarget) {
        this.dailyStepTarget = dailyStepTarget;
    }

    public String getUnitSystem() {
        return unitSystem;
    }

    public void setUnitSystem(String unitSystem) {
        this.unitSystem = unitSystem;
    }
}

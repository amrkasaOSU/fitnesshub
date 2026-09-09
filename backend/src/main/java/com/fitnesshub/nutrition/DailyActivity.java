package com.fitnesshub.nutrition;

import com.fitnesshub.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "daily_activities", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"client_id", "date"})
})
public class DailyActivity extends BaseEntity {

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private Integer steps;

    @Column(name = "estimated_active_calories", precision = 7, scale = 2)
    private BigDecimal estimatedActiveCalories;

    @Column(name = "estimated_total_calories", precision = 7, scale = 2)
    private BigDecimal estimatedTotalCalories;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DailyActivitySource source = DailyActivitySource.ESTIMATED;

    protected DailyActivity() {
    }

    public DailyActivity(UUID clientId, LocalDate date, Integer steps) {
        this.clientId = clientId;
        this.date = date;
        this.steps = steps;
    }

    public UUID getClientId() {
        return clientId;
    }

    public LocalDate getDate() {
        return date;
    }

    public Integer getSteps() {
        return steps;
    }

    public void setSteps(Integer steps) {
        this.steps = steps;
    }

    public BigDecimal getEstimatedActiveCalories() {
        return estimatedActiveCalories;
    }

    public void setEstimatedActiveCalories(BigDecimal estimatedActiveCalories) {
        this.estimatedActiveCalories = estimatedActiveCalories;
    }

    public BigDecimal getEstimatedTotalCalories() {
        return estimatedTotalCalories;
    }

    public void setEstimatedTotalCalories(BigDecimal estimatedTotalCalories) {
        this.estimatedTotalCalories = estimatedTotalCalories;
    }

    public DailyActivitySource getSource() {
        return source;
    }

    public void setSource(DailyActivitySource source) {
        this.source = source;
    }
}

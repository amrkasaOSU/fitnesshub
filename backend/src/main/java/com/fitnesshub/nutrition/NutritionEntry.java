package com.fitnesshub.nutrition;

import com.fitnesshub.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "nutrition_entries", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"client_id", "date"})
})
public class NutritionEntry extends BaseEntity {

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false, precision = 7, scale = 2)
    private BigDecimal calories;

    @Column(name = "protein_grams", nullable = false, precision = 6, scale = 2)
    private BigDecimal proteinGrams;

    @Column(name = "carbohydrates_grams", precision = 6, scale = 2)
    private BigDecimal carbohydratesGrams;

    @Column(name = "fat_grams", precision = 6, scale = 2)
    private BigDecimal fatGrams;

    @Column(name = "fiber_grams", precision = 6, scale = 2)
    private BigDecimal fiberGrams;

    @Column(name = "water_ml")
    private Integer waterMl;

    @Column(columnDefinition = "text")
    private String notes;

    protected NutritionEntry() {
    }

    public NutritionEntry(UUID clientId, LocalDate date, BigDecimal calories, BigDecimal proteinGrams) {
        this.clientId = clientId;
        this.date = date;
        this.calories = calories;
        this.proteinGrams = proteinGrams;
    }

    public UUID getClientId() {
        return clientId;
    }

    public LocalDate getDate() {
        return date;
    }

    public BigDecimal getCalories() {
        return calories;
    }

    public void setCalories(BigDecimal calories) {
        this.calories = calories;
    }

    public BigDecimal getProteinGrams() {
        return proteinGrams;
    }

    public void setProteinGrams(BigDecimal proteinGrams) {
        this.proteinGrams = proteinGrams;
    }

    public BigDecimal getCarbohydratesGrams() {
        return carbohydratesGrams;
    }

    public void setCarbohydratesGrams(BigDecimal carbohydratesGrams) {
        this.carbohydratesGrams = carbohydratesGrams;
    }

    public BigDecimal getFatGrams() {
        return fatGrams;
    }

    public void setFatGrams(BigDecimal fatGrams) {
        this.fatGrams = fatGrams;
    }

    public BigDecimal getFiberGrams() {
        return fiberGrams;
    }

    public void setFiberGrams(BigDecimal fiberGrams) {
        this.fiberGrams = fiberGrams;
    }

    public Integer getWaterMl() {
        return waterMl;
    }

    public void setWaterMl(Integer waterMl) {
        this.waterMl = waterMl;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}

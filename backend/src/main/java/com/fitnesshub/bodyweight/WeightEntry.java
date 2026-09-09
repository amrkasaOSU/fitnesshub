package com.fitnesshub.bodyweight;

import com.fitnesshub.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "weight_entries")
public class WeightEntry extends BaseEntity {

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal weight;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(columnDefinition = "text")
    private String notes;

    protected WeightEntry() {
    }

    public WeightEntry(UUID clientId, BigDecimal weight, Instant recordedAt) {
        this.clientId = clientId;
        this.weight = weight;
        this.recordedAt = recordedAt;
    }

    public UUID getClientId() {
        return clientId;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public void setWeight(BigDecimal weight) {
        this.weight = weight;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(Instant recordedAt) {
        this.recordedAt = recordedAt;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}

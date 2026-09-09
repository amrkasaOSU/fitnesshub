package com.fitnesshub.steps;

import com.fitnesshub.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "step_entries", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"client_id", "date"})
})
public class StepEntry extends BaseEntity {

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(nullable = false)
    private Integer steps;

    @Column(nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StepSource source = StepSource.MANUAL;

    protected StepEntry() {
    }

    public StepEntry(UUID clientId, Integer steps, LocalDate date, StepSource source) {
        this.clientId = clientId;
        this.steps = steps;
        this.date = date;
        this.source = source;
    }

    public UUID getClientId() {
        return clientId;
    }

    public Integer getSteps() {
        return steps;
    }

    public void setSteps(Integer steps) {
        this.steps = steps;
    }

    public LocalDate getDate() {
        return date;
    }

    public StepSource getSource() {
        return source;
    }

    public void setSource(StepSource source) {
        this.source = source;
    }
}

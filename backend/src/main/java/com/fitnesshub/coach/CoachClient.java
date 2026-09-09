package com.fitnesshub.coach;

import com.fitnesshub.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "coach_clients", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"coach_id", "client_id"})
})
public class CoachClient extends BaseEntity {

    @Column(name = "coach_id", nullable = false)
    private UUID coachId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CoachClientStatus status = CoachClientStatus.ACTIVE;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    protected CoachClient() {
    }

    public CoachClient(UUID coachId, UUID clientId) {
        this.coachId = coachId;
        this.clientId = clientId;
        this.startedAt = Instant.now();
    }

    public UUID getCoachId() {
        return coachId;
    }

    public UUID getClientId() {
        return clientId;
    }

    public CoachClientStatus getStatus() {
        return status;
    }

    public void setStatus(CoachClientStatus status) {
        this.status = status;
        if (status == CoachClientStatus.ENDED) {
            this.endedAt = Instant.now();
        }
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }
}

package com.fitnesshub.messaging;

import com.fitnesshub.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(name = "conversations", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"coach_id", "client_id"})
})
public class Conversation extends BaseEntity {

    @Column(name = "coach_id", nullable = false)
    private UUID coachId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    protected Conversation() {
    }

    public Conversation(UUID coachId, UUID clientId) {
        this.coachId = coachId;
        this.clientId = clientId;
    }

    public UUID getCoachId() {
        return coachId;
    }

    public UUID getClientId() {
        return clientId;
    }
}

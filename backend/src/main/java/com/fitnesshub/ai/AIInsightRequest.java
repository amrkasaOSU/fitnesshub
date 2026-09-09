package com.fitnesshub.ai;

import com.fitnesshub.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "ai_insight_requests")
public class AIInsightRequest extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "client_id")
    private UUID clientId;

    @Column(nullable = false, columnDefinition = "text")
    private String question;

    protected AIInsightRequest() {
    }

    public AIInsightRequest(UUID userId, UUID clientId, String question) {
        this.userId = userId;
        this.clientId = clientId;
        this.question = question;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getClientId() {
        return clientId;
    }

    public String getQuestion() {
        return question;
    }
}

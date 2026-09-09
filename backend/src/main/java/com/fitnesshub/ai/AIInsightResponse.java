package com.fitnesshub.ai;

import com.fitnesshub.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "ai_insight_responses")
public class AIInsightResponse extends BaseEntity {

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(nullable = false, columnDefinition = "text")
    private String response;

    @Column(nullable = false)
    private String model;

    protected AIInsightResponse() {
    }

    public AIInsightResponse(UUID requestId, String response, String model) {
        this.requestId = requestId;
        this.response = response;
        this.model = model;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public String getResponse() {
        return response;
    }

    public String getModel() {
        return model;
    }
}

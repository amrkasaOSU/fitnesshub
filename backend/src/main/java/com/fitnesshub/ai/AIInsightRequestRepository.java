package com.fitnesshub.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AIInsightRequestRepository extends JpaRepository<AIInsightRequest, UUID> {
}

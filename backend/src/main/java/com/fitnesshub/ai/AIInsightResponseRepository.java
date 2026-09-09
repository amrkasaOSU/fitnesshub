package com.fitnesshub.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AIInsightResponseRepository extends JpaRepository<AIInsightResponse, UUID> {
}

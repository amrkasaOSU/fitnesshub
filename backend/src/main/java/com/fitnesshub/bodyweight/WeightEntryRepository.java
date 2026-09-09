package com.fitnesshub.bodyweight;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface WeightEntryRepository extends JpaRepository<WeightEntry, UUID> {
    Page<WeightEntry> findByClientIdOrderByRecordedAtDesc(UUID clientId, Pageable pageable);

    List<WeightEntry> findByClientIdAndRecordedAtBetweenOrderByRecordedAtAsc(UUID clientId, Instant from, Instant to);

    List<WeightEntry> findByClientIdOrderByRecordedAtAsc(UUID clientId);
}

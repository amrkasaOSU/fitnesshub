package com.fitnesshub.steps;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StepEntryRepository extends JpaRepository<StepEntry, UUID> {
    Optional<StepEntry> findByClientIdAndDate(UUID clientId, LocalDate date);

    Page<StepEntry> findByClientIdOrderByDateDesc(UUID clientId, Pageable pageable);

    List<StepEntry> findByClientIdAndDateBetweenOrderByDateAsc(UUID clientId, LocalDate from, LocalDate to);
}

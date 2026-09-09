package com.fitnesshub.nutrition;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NutritionEntryRepository extends JpaRepository<NutritionEntry, UUID> {
    Optional<NutritionEntry> findByClientIdAndDate(UUID clientId, LocalDate date);

    Page<NutritionEntry> findByClientIdOrderByDateDesc(UUID clientId, Pageable pageable);

    List<NutritionEntry> findByClientIdAndDateBetweenOrderByDateAsc(UUID clientId, LocalDate from, LocalDate to);
}

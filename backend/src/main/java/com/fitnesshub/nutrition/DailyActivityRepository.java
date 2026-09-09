package com.fitnesshub.nutrition;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface DailyActivityRepository extends JpaRepository<DailyActivity, UUID> {
    Optional<DailyActivity> findByClientIdAndDate(UUID clientId, LocalDate date);
}

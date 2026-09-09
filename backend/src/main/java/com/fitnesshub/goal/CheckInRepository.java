package com.fitnesshub.goal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CheckInRepository extends JpaRepository<CheckIn, UUID> {
    Page<CheckIn> findByClientIdOrderByWeekStartDateDesc(UUID clientId, Pageable pageable);

    Optional<CheckIn> findByClientIdAndWeekStartDate(UUID clientId, LocalDate weekStartDate);

    List<CheckIn> findByClientIdInAndCoachReviewedAtIsNullOrderBySubmittedAtAsc(List<UUID> clientIds);

    Optional<CheckIn> findFirstByClientIdOrderByWeekStartDateDesc(UUID clientId);
}

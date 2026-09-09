package com.fitnesshub.workout;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkoutSessionRepository extends JpaRepository<WorkoutSession, UUID> {

    Page<WorkoutSession> findByClientIdOrderByStartedAtDesc(UUID clientId, Pageable pageable);

    List<WorkoutSession> findByClientIdAndStartedAtBetween(UUID clientId, Instant from, Instant to);

    Optional<WorkoutSession> findFirstByClientIdAndStatusOrderByStartedAtDesc(UUID clientId, WorkoutSessionStatus status);

    @Query("select w from WorkoutSession w where w.clientId = :clientId and w.status = 'COMPLETED' " +
            "order by w.startedAt desc")
    List<WorkoutSession> findCompletedForClient(@Param("clientId") UUID clientId);

    long countByClientIdAndStatusAndStartedAtBetween(UUID clientId, WorkoutSessionStatus status, Instant from, Instant to);
}

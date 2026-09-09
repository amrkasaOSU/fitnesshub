package com.fitnesshub.coach;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CoachClientRepository extends JpaRepository<CoachClient, UUID> {
    List<CoachClient> findByCoachId(UUID coachId);

    Optional<CoachClient> findByCoachIdAndClientId(UUID coachId, UUID clientId);

    Optional<CoachClient> findFirstByClientIdAndStatus(UUID clientId, CoachClientStatus status);

    boolean existsByCoachIdAndClientIdAndStatus(UUID coachId, UUID clientId, CoachClientStatus status);
}

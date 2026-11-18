package com.fitnesshub.client;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientProfileRepository extends JpaRepository<ClientProfile, UUID> {
    Optional<ClientProfile> findByUserId(UUID userId);

    List<ClientProfile> findByUserIdIn(List<UUID> userIds);

    Optional<ClientProfile> findByUserIdAndCoachId(UUID userId, UUID coachId);
}

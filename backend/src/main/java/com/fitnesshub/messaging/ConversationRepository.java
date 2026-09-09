package com.fitnesshub.messaging;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
    Optional<Conversation> findByCoachIdAndClientId(UUID coachId, UUID clientId);

    List<Conversation> findByCoachId(UUID coachId);

    List<Conversation> findByClientId(UUID clientId);
}

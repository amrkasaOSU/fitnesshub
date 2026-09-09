package com.fitnesshub.coach;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CoachNoteRepository extends JpaRepository<CoachNote, UUID> {
    List<CoachNote> findByCoachIdAndClientIdOrderByCreatedAtDesc(UUID coachId, UUID clientId);
}

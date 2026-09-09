package com.fitnesshub.progress;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PersonalRecordRepository extends JpaRepository<PersonalRecord, UUID> {

    Optional<PersonalRecord> findByClientIdAndExerciseIdAndRecordType(UUID clientId, UUID exerciseId, RecordType recordType);

    List<PersonalRecord> findByClientIdAndExerciseId(UUID clientId, UUID exerciseId);

    List<PersonalRecord> findByClientIdOrderByAchievedAtDesc(UUID clientId);

    List<PersonalRecord> findTop10ByClientIdOrderByAchievedAtDesc(UUID clientId);

    List<PersonalRecord> findByClientIdAndAchievedAtBetween(UUID clientId, Instant from, Instant to);
}

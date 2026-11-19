package com.fitnesshub.progress;

import org.springframework.data.domain.Pageable;
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

    /**
     * Newest records first. The id is a tie-breaker: one logged set can beat
     * several record types at the same instant, and without a second key the
     * order (and so which rows survive a limit) would vary between calls.
     */
    List<PersonalRecord> findByClientIdInOrderByAchievedAtDescIdAsc(List<UUID> clientIds, Pageable pageable);

    List<PersonalRecord> findByClientIdOrderByAchievedAtDescIdAsc(UUID clientId, Pageable pageable);

    List<PersonalRecord> findByClientIdAndAchievedAtBetween(UUID clientId, Instant from, Instant to);
}

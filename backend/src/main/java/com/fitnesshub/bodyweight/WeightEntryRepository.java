package com.fitnesshub.bodyweight;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface WeightEntryRepository extends JpaRepository<WeightEntry, UUID> {
    Page<WeightEntry> findByClientIdOrderByRecordedAtDesc(UUID clientId, Pageable pageable);

    List<WeightEntry> findByClientIdAndRecordedAtBetweenOrderByRecordedAtAsc(UUID clientId, Instant from, Instant to);

    List<WeightEntry> findByClientIdOrderByRecordedAtAsc(UUID clientId);

    List<WeightEntry> findByClientIdInAndRecordedAtGreaterThanEqualOrderByRecordedAtAsc(
            List<UUID> clientIds, Instant since);

    /** Each client's most recent weigh-in, one row per client. */
    @Query(value = """
            select distinct on (client_id) *
            from weight_entries
            where client_id in (:clientIds)
            order by client_id, recorded_at desc
            """, nativeQuery = true)
    List<WeightEntry> findLatestPerClient(@Param("clientIds") List<UUID> clientIds);
}

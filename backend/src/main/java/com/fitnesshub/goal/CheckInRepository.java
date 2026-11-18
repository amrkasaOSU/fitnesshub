package com.fitnesshub.goal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CheckInRepository extends JpaRepository<CheckIn, UUID> {
    Page<CheckIn> findByClientIdOrderByWeekStartDateDesc(UUID clientId, Pageable pageable);

    Optional<CheckIn> findByClientIdAndWeekStartDate(UUID clientId, LocalDate weekStartDate);

    List<CheckIn> findByClientIdInAndCoachReviewedAtIsNullOrderBySubmittedAtAsc(List<UUID> clientIds);

    Optional<CheckIn> findFirstByClientIdOrderByWeekStartDateDesc(UUID clientId);

    /** Each client's check-in for the latest week they submitted one, one row per client. */
    @Query(value = """
            select distinct on (client_id) *
            from check_ins
            where client_id in (:clientIds)
            order by client_id, week_start_date desc
            """, nativeQuery = true)
    List<CheckIn> findLatestPerClient(@Param("clientIds") List<UUID> clientIds);

    long countByClientIdInAndCoachReviewedAtIsNull(List<UUID> clientIds);
}

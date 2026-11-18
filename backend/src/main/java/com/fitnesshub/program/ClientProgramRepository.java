package com.fitnesshub.program;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientProgramRepository extends JpaRepository<ClientProgram, UUID> {
    List<ClientProgram> findByClientIdOrderByStartDateDesc(UUID clientId);

    Optional<ClientProgram> findFirstByClientIdAndStatusOrderByStartDateDesc(UUID clientId, ClientProgramStatus status);

    List<ClientProgram> findByProgramId(UUID programId);

    /** Assignment counts for many programs at once. Columns: program_id, count. */
    @org.springframework.data.jpa.repository.Query("""
            select cp.programId, count(cp) from ClientProgram cp
            where cp.programId in :programIds group by cp.programId
            """)
    List<Object[]> countAssignmentsPerProgram(@org.springframework.data.repository.query.Param("programIds") List<UUID> programIds);

    List<ClientProgram> findByClientIdInAndStatus(List<UUID> clientIds, ClientProgramStatus status);
}

package com.fitnesshub.program;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProgramDayRepository extends JpaRepository<ProgramDay, UUID> {
    List<ProgramDay> findByProgramIdOrderByDayNumberAsc(UUID programId);

    List<ProgramDay> findByProgramIdInOrderByDayNumberAsc(List<UUID> programIds);

    /** Day counts for many programs at once. Columns: program_id, count. */
    @org.springframework.data.jpa.repository.Query("""
            select d.programId, count(d) from ProgramDay d
            where d.programId in :programIds group by d.programId
            """)
    List<Object[]> countDaysPerProgram(@org.springframework.data.repository.query.Param("programIds") List<UUID> programIds);
}

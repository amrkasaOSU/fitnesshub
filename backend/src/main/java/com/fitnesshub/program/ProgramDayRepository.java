package com.fitnesshub.program;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProgramDayRepository extends JpaRepository<ProgramDay, UUID> {
    List<ProgramDay> findByProgramIdOrderByDayNumberAsc(UUID programId);
}

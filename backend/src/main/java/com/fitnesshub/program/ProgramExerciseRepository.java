package com.fitnesshub.program;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProgramExerciseRepository extends JpaRepository<ProgramExercise, UUID> {
    List<ProgramExercise> findByProgramDayIdOrderByOrderIndexAsc(UUID programDayId);

    List<ProgramExercise> findByProgramDayIdInOrderByOrderIndexAsc(List<UUID> programDayIds);
}

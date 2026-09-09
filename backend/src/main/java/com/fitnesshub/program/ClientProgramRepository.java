package com.fitnesshub.program;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientProgramRepository extends JpaRepository<ClientProgram, UUID> {
    List<ClientProgram> findByClientIdOrderByStartDateDesc(UUID clientId);

    Optional<ClientProgram> findFirstByClientIdAndStatusOrderByStartDateDesc(UUID clientId, ClientProgramStatus status);

    List<ClientProgram> findByProgramId(UUID programId);
}

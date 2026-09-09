package com.fitnesshub.program;

import com.fitnesshub.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "client_programs")
public class ClientProgram extends BaseEntity {

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "program_id", nullable = false)
    private UUID programId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClientProgramStatus status = ClientProgramStatus.ACTIVE;

    protected ClientProgram() {
    }

    public ClientProgram(UUID clientId, UUID programId, LocalDate startDate) {
        this.clientId = clientId;
        this.programId = programId;
        this.startDate = startDate;
    }

    public UUID getClientId() {
        return clientId;
    }

    public UUID getProgramId() {
        return programId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public ClientProgramStatus getStatus() {
        return status;
    }

    public void setStatus(ClientProgramStatus status) {
        this.status = status;
    }
}

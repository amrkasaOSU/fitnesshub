package com.fitnesshub.program.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record AssignProgramRequest(
        @NotNull UUID clientId,
        @NotNull LocalDate startDate
) {
}

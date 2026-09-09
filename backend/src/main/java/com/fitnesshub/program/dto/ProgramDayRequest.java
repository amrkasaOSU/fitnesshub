package com.fitnesshub.program.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ProgramDayRequest(
        @NotNull @Min(1) Integer dayNumber,
        @NotBlank String name,
        String description,
        @Valid List<ProgramExerciseRequest> exercises
) {
}

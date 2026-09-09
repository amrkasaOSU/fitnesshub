package com.fitnesshub.program.dto;

import com.fitnesshub.client.FitnessGoal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ProgramRequest(
        @NotBlank String name,
        String description,
        @NotNull @Min(1) Integer durationWeeks,
        @NotNull FitnessGoal goal,
        @Valid List<ProgramDayRequest> days
) {
}

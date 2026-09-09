package com.fitnesshub.steps.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;

public record LogStepsRequest(
        @NotNull @PositiveOrZero Integer steps,
        LocalDate date
) {
}

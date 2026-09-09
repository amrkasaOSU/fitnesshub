package com.fitnesshub.steps.dto;

import com.fitnesshub.steps.StepSource;

import java.time.LocalDate;
import java.util.UUID;

public record StepEntryDto(UUID id, Integer steps, LocalDate date, StepSource source) {
}

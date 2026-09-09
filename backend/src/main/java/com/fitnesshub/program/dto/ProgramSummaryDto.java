package com.fitnesshub.program.dto;

import com.fitnesshub.client.FitnessGoal;

import java.util.UUID;

public record ProgramSummaryDto(
        UUID id,
        String name,
        int durationWeeks,
        FitnessGoal goal,
        int version,
        int dayCount,
        int assignedClientCount
) {
}

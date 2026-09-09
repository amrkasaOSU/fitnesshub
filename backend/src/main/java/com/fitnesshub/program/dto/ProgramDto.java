package com.fitnesshub.program.dto;

import com.fitnesshub.client.FitnessGoal;

import java.util.List;
import java.util.UUID;

public record ProgramDto(
        UUID id,
        UUID coachId,
        String name,
        String description,
        int durationWeeks,
        FitnessGoal goal,
        int version,
        List<ProgramDayDto> days
) {
}

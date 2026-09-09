package com.fitnesshub.program.dto;

import java.util.List;
import java.util.UUID;

public record ProgramDayDto(
        UUID id,
        int dayNumber,
        String name,
        String description,
        List<ProgramExerciseDto> exercises
) {
}

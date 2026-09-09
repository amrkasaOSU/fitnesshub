package com.fitnesshub.progress.dto;

import com.fitnesshub.progress.RecordType;

import java.time.Instant;
import java.util.UUID;

public record PersonalRecordDto(
        UUID id,
        UUID clientId,
        UUID exerciseId,
        String exerciseName,
        RecordType recordType,
        String value,
        Instant achievedAt
) {
}

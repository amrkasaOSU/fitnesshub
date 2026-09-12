package com.fitnesshub.photo.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Metadata only - the bytes are fetched separately from /api/progress-photos/{id}/image. */
public record ProgressPhotoDto(
        UUID id,
        LocalDate takenOn,
        String caption,
        String contentType,
        Integer sizeBytes,
        Instant createdAt
) {
}

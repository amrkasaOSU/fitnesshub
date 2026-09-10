package com.fitnesshub.workout.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The reason is required - it's the entire point of telling the coach. */
public record SkipWorkoutRequest(
        @NotBlank @Size(max = 500) String reason
) {
}

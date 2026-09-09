package com.fitnesshub.workout.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateFeedbackRequest(@NotBlank String content) {
}

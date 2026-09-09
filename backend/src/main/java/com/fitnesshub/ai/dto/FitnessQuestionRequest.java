package com.fitnesshub.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record FitnessQuestionRequest(
        UUID clientId,
        @NotBlank @Size(max = 2000) String question
) {
}

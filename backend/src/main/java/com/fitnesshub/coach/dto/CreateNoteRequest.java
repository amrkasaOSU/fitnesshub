package com.fitnesshub.coach.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateNoteRequest(@NotBlank String content) {
}

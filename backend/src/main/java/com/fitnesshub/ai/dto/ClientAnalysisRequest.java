package com.fitnesshub.ai.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ClientAnalysisRequest(@NotNull UUID clientId) {
}

package com.fitnesshub.ai.dto;

import java.util.List;

public record AiAnswer(
        String answer,
        String confidence,
        List<String> relevantMetrics,
        String recommendation,
        String reasoningSummary,
        List<String> warnings
) {
    public static AiAnswer notConfigured() {
        return new AiAnswer(
                "The AI assistant isn't configured yet. Ask your coach, or check back once an OpenAI API key has been added.",
                "n/a", List.of(), null, null, List.of());
    }

    public static AiAnswer providerError(String message) {
        return new AiAnswer(
                "The AI assistant couldn't process that request right now. Please try again shortly.",
                "n/a", List.of(), null, message, List.of());
    }
}

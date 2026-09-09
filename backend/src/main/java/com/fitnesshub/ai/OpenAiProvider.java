package com.fitnesshub.ai;

import com.fitnesshub.ai.dto.AiAnswer;
import com.fitnesshub.ai.dto.FitnessContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Talks to OpenAI's Chat Completions API directly over java.net.http (no SDK
 * dependency). The model is instructed to return a strict JSON object
 * matching {@link AiAnswer}'s shape; on any failure to configure, reach, or
 * parse the provider, this returns a graceful AiAnswer instead of throwing -
 * the AI assistant must never be able to 500 the whole request.
 */
@Component
public class OpenAiProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiProvider.class);
    private static final String SAFETY_SYSTEM_PROMPT = """
            You are the FitnessHub AI Fitness Assistant, embedded in a fitness coaching platform.
            You answer using ONLY the structured training/nutrition/progress data provided to you as
            JSON context - never invent numbers that aren't in that context.

            Hard rules:
            - Never diagnose injuries or medical conditions.
            - Never diagnose or discuss diseases.
            - Never prescribe or recommend medication.
            - Never claim to replace a doctor, physical therapist, dietitian, or other qualified professional.
            - Never guarantee results.
            - Never give dangerous training instructions (e.g. ignore pain, train through sharp pain, unsafe form cues).
            - If the question touches pain, injury, or a medical condition, recommend consulting an
              appropriately qualified professional, and say so in your "warnings".
            - You may give data-driven training suggestions (e.g. a suggested weight range) based on the
              provided context, but you must never claim to have changed the user's program - only the
              coach or client can actually do that.

            Respond with ONLY a JSON object with this exact shape, no markdown fences, no extra text:
            {
              "answer": "string, the natural-language answer",
              "confidence": "low|medium|high",
              "relevantMetrics": ["short strings describing which context fields drove the answer"],
              "recommendation": "string or null, a concrete suggestion if applicable",
              "reasoningSummary": "string, 1-3 sentences on how the data led to this answer",
              "warnings": ["short strings, e.g. medical-disclaimer language, empty array if none apply"]
            }
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Value("${fitnesshub.ai.openai.api-key:}")
    private String apiKey;

    @Value("${fitnesshub.ai.openai.model:gpt-4o-mini}")
    private String model;

    @Value("${fitnesshub.ai.openai.base-url:https://api.openai.com/v1}")
    private String baseUrl;

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public AiAnswer answerFitnessQuestion(FitnessContext context, String question) {
        String userPrompt = "Client's question: \"" + question + "\"\n\nContext JSON:\n" + toJson(context);
        return call(userPrompt);
    }

    @Override
    public AiAnswer analyzeExerciseProgress(FitnessContext context) {
        String userPrompt = "Analyze this client's progress on the exercise described in the context and explain "
                + "the trend in plain language.\n\nContext JSON:\n" + toJson(context);
        return call(userPrompt);
    }

    @Override
    public AiAnswer summarizeClientProgress(FitnessContext context) {
        String userPrompt = "You are helping a coach prepare for a check-in with this client. Summarize top "
                + "observations, potential concerns, positive progress, and 2-3 questions the coach should ask "
                + "the client this week, based only on the context JSON below.\n\nContext JSON:\n" + toJson(context);
        return call(userPrompt);
    }

    private AiAnswer call(String userPrompt) {
        if (!isConfigured()) {
            return AiAnswer.notConfigured();
        }
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);
            body.put("temperature", 0.3);
            ArrayNode messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", SAFETY_SYSTEM_PROMPT);
            messages.addObject().put("role", "user").put("content", userPrompt);
            body.putObject("response_format").put("type", "json_object");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("OpenAI call failed with status {}: {}", response.statusCode(), truncate(response.body()));
                return AiAnswer.providerError("Provider returned HTTP " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText();
            return parseAnswer(content);
        } catch (Exception e) {
            log.error("OpenAI call threw an exception", e);
            return AiAnswer.providerError(e.getMessage());
        }
    }

    private AiAnswer parseAnswer(String content) {
        try {
            JsonNode node = objectMapper.readTree(content);
            List<String> metrics = toStringList(node.get("relevantMetrics"));
            List<String> warnings = toStringList(node.get("warnings"));
            return new AiAnswer(
                    node.path("answer").asText(null),
                    node.path("confidence").asText("medium"),
                    metrics,
                    node.hasNonNull("recommendation") ? node.get("recommendation").asText() : null,
                    node.path("reasoningSummary").asText(null),
                    warnings
            );
        } catch (Exception e) {
            log.warn("Could not parse AI provider response as structured JSON: {}", truncate(content));
            return new AiAnswer(content, "low", List.of(), null,
                    "The provider's response could not be parsed into structured fields.", List.of());
        }
    }

    private List<String> toStringList(JsonNode arrayNode) {
        List<String> result = new ArrayList<>();
        if (arrayNode != null && arrayNode.isArray()) {
            arrayNode.forEach(n -> result.add(n.asText()));
        }
        return result;
    }

    private String toJson(FitnessContext context) {
        try {
            return objectMapper.writeValueAsString(context);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String truncate(String s) {
        return s == null ? "" : s.length() > 500 ? s.substring(0, 500) : s;
    }
}
